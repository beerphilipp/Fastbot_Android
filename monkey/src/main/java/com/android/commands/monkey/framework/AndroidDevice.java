/*
 * Copyright 2020 Advanced Software Technologies Lab at ETH Zurich, Switzerland
 *
 * Modified - Copyright (c) 2020 Bytedance Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.commands.monkey.framework;


import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.IActivityManager;
import android.app.admin.IDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.input.IInputManager;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.commands.monkey.utils.ContextUtils;
import com.android.commands.monkey.utils.InputUtils;
import com.android.commands.monkey.utils.Logger;
import com.android.commands.monkey.utils.Utils;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.view.IInputMethodManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.commands.monkey.utils.Config.clearPackage;
import static com.android.commands.monkey.utils.Config.enableStopPackage;
import static com.android.commands.monkey.utils.Config.grantAllPermission;


/**
 * @author Zhao Zhang, Tianxiao Gu
 */

/**
 * Android framework utils
 */
public class AndroidDevice {

    public static IInputManager iInputManager;

    public static IActivityManager iActivityManager;
    public static IWindowManager iWindowManager;
    public static IPackageManager iPackageManager;
    public static PackageManager packageManager;
    public static IDevicePolicyManager iDevicePolicyManager;
    public static IStatusBarService iStatusBarService;
    public static IInputMethodManager iInputMethodManager;
    public static InputMethodManager inputMethodManager;
    public static IPowerManager iPowerManager;
    public static boolean useADBKeyboard;
    public static Set<String> inputMethodPackages = new HashSet<>();
    static Pattern DISPLAY_FOCUSED_STACK_PATTERN = Pattern.compile("mLastFocusedStack=Task[{][a-z0-9]+.*StackId=([0-9]+).*");
    static Pattern FOCUSED_STACK_PATTERN = Pattern.compile("mFocusedStack=ActivityStack[{][a-z0-9]+ stackId=([0-9]+), [0-9]+ tasks[}]");
    static Pattern DISPLAY_PATTERN = Pattern.compile("^Display #([0-9]+) .*:$");
    static Pattern STACK_PATTERN = Pattern.compile("^  Stack #([0-9]+):.*$");
    static Pattern TASK_PATTERN = Pattern.compile("^    \\* Task.*#([0-9]+).*$");
    static Pattern ACTIVITY_PATTERN = Pattern.compile("^      [*] Hist #[0-9]+: ActivityRecord[{][0-9a-z]+ u[0-9]+ ([^ /]+)/([^ ]+) t[0-9]+[}]$");
    private static Set<String> blacklistPermissions = new HashSet<String>();
    /**
     * https://github.com/senzhk/ADBKeyBoard
     */
    private static String IME_MESSAGE = "ADB_INPUT_TEXT";
    private static String IME_CHARS = "ADB_INPUT_CHARS";
    private static String IME_KEYCODE = "ADB_INPUT_CODE";
    private static String IME_EDITORCODE = "ADB_EDITOR_CODE";
    private static String IME_ADB_KEYBOARD;

    public static void initializeAndroidDevice(IActivityManager mAm, IWindowManager mWm, IPackageManager mPm, String keyboard) {
        iActivityManager = mAm;
        iWindowManager = mWm;
        iPackageManager = mPm;
        IME_ADB_KEYBOARD = keyboard;

        IBinder binder = ServiceManager.getService(Context.INPUT_SERVICE);
        iInputManager = IInputManager.Stub.asInterface(binder);

        inputMethodManager = (InputMethodManager) ContextUtils.getSystemContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        packageManager = ContextUtils.getSystemContext().getPackageManager();
        iDevicePolicyManager = IDevicePolicyManager.Stub.asInterface(ServiceManager.getService("device_policy"));
        if (iDevicePolicyManager == null) {
            System.err.println("** Error: Unable to connect to deveice policy manager; is the system " + "running?");
        }

        iStatusBarService = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
        if (iStatusBarService == null) {
            System.err.println("** Error: Unable to connect to status bar service; is the system " + "running?");
        }

        iInputMethodManager = IInputMethodManager.Stub.asInterface(ServiceManager.getService("input_method"));
        if (iInputMethodManager == null) {
            System.err.println(
                    "** Error: Unable to connect to input method manager service; is the system " + "running?");
        }

        AndroidDevice.useADBKeyboard = enableADBKeyboard();

        iPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
        if (iPowerManager == null) {
            System.err.println("** Error: Unable to connect to power manager service; is the system " + "running?");
        }
    }

    /**
     * Try to get all enabled keyboards, and find ADBKeyboard
     * @return If ADBKeyboard IME exists and enabled, return true, false otherwise.
     */
    private static boolean enableADBKeyboard() {
        List<InputMethodInfo> inputMethods = AndroidDevice.inputMethodManager.getEnabledInputMethodList();
        if (inputMethods != null) {
            for (InputMethodInfo imi : inputMethods) {
                Logger.println("InputMethod ID: " + imi.getId());
                if (IME_ADB_KEYBOARD.equals(imi.getId())) {
                    Logger.println("Find Keyboard: " + IME_ADB_KEYBOARD);
                    return true;
                }
            }
        }
        return false;
    }

    public static Rect getDisplayBounds() {
        android.view.Display display = DisplayManagerGlobal.getInstance().getRealDisplay(android.view.Display.DEFAULT_DISPLAY);
        Point size = new Point();
        display.getSize(size);
        Rect bounds = new Rect();
        bounds.top = 0;
        bounds.left = 0;
        bounds.right = size.x;
        bounds.bottom = size.y;
        return bounds;
    }

    public static ComponentName getTopActivityComponentName() {
        try {
            List<RunningTaskInfo> taskInfo = APIAdapter.getTasks(AndroidDevice.iActivityManager, Integer.MAX_VALUE);
            if (taskInfo != null && !taskInfo.isEmpty()) {
                RunningTaskInfo task = taskInfo.get(0);
                return task.topActivity;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static List<ActivityName> getCurrentTaskActivityStack() {
        StackInfo stackInfo = getFocusedStack();
        if (stackInfo != null && !stackInfo.getTasks().isEmpty()) {
            return stackInfo.getTasks().get(0).activityNames;
        }
        return null;
    }

    public static boolean isVirtualKeyboardOpened() {
        try {
            String[] cmd = {
                    "/bin/sh",
                    "-c",
                    "dumpsys input_method | grep mInputShown"
            };
            Process process =  Runtime.getRuntime().exec(cmd);
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(process.getInputStream()));
            String s;
            Pattern pattern = Pattern.compile("minputshown=true", Pattern.CASE_INSENSITIVE);
            while ((s = stdInput.readLine()) != null) {
                Logger.println(s = s.toLowerCase(Locale.ENGLISH));
                Matcher matcher = pattern.matcher(s);
                boolean matchFound = matcher.find();
                if(matchFound) {
                    Logger.println("mInputShown found.");
                    return true;
                }
            }
        }catch (IOException ioException){
            Logger.errorPrintln("adb executing \"dumpsys input_method | grep mInputShown\" wrong!");
        }
        Logger.println("mInputShown not found, getInputMethodWindowVisibleHeight is used.");
        int height = AndroidDevice.inputMethodManager.getInputMethodWindowVisibleHeight();
        return height != 0;
    }

    public static void checkInteractive() {
        try {
            if (!iPowerManager.isInteractive()) {
                Logger.format("Power Manager says we are NOT interactive");
                int ret = Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"}).waitFor();
                Logger.format("Wakeup ret code %d %s", ret, (iPowerManager.isInteractive() ? "Interactive" : "Not interactive"));
            } else {
                Logger.format("Power Manager says we are interactive");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static boolean checkAndSetInputMethod() {
        try {
            if (!useADBKeyboard) {
                return false;
            }
            InputUtils.switchToIme(IME_ADB_KEYBOARD);
            return true;
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String[] getGrantedPermissions(String packageName) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);

            if (packageInfo == null) {
                return new String[0];
            }
            if (packageInfo.requestedPermissions == null) {
                return new String[0];
            }
            for (String s : packageInfo.requestedPermissions) {
                Logger.debugFormat("%s requrested permission %s", packageName, s);
            }

            return packageInfo.requestedPermissions;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean grantRuntimePermission(String packageName, String permission) {
        try {
            int ret = executeCommandAndWaitFor(new String[]{"pm", "grant", packageName, permission});
            return ret == 0;
        } catch (Exception e) {
            Logger.warningFormat("Granting saved permission %s to %s results in error %s", permission, packageName, e);
        }
        return false;
    }

    public static boolean grantRuntimePermissions(String packageName, String[] savedPermissions, String reason) {
        try {
            Logger.infoFormat("Try to grant saved permission to %s for %s... ", packageName, reason);
            for (String permission : savedPermissions) {
                try {
                    Logger.infoFormat("Grant saved permission %s to %s... ", permission, packageName);
                    if (grantRuntimePermission(packageName, permission)) {
                        Logger.infoFormat("Permission %s is granted to %s... ", permission, packageName);
                    } else {
                        Logger.infoFormat("Permission %s is NOT granted to %s... ", permission, packageName);
                    }
                } catch (RuntimeException e) {
                    if (!blacklistPermissions.contains(permission)) {
                        Logger.warningFormat("Granting saved permission %s top %s results in error %s", permission,
                                packageName, e);
                    }
                    blacklistPermissions.add(permission);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static void waitForNotify(Object lock) {
        synchronized (lock) {
            try {
                lock.wait(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } // at most wait for 5s
        }
    }

    /**
     * Used for getting package size, but could only be used when API version is no more than 26,
     * otherwise it will throw exceptions.
     * @param packageName The name of package of which you want to check size
     * @return If the query is successful, return true.
     */
    public static boolean checkNativeApp(String packageName) {
        final PackageStats[] result = new PackageStats[1];
        try {
            IPackageStatsObserver observer = new IPackageStatsObserver() {

                @Override
                public IBinder asBinder() {
                    return null;
                }

                @Override
                public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) throws RemoteException {
                    synchronized (this) {
                        if (succeeded) {
                            result[0] = pStats;
                        }
                        this.notifyAll();
                    }

                }

            };
            iPackageManager.getPackageSizeInfo(packageName, UserHandle.myUserId(), observer);
            waitForNotify(observer);
            if (result[0] != null) {
                PackageStats stat = result[0];
                Logger.format("Code size: %d", stat.codeSize);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (java.lang.UnsupportedOperationException e){
            Logger.errorPrintln("Operation of getting package size is not support above api 26.");
        }
        return false;
    }

    public static int executeCommandAndWaitFor(String[] cmd) throws InterruptedException, IOException {
        return Runtime.getRuntime().exec(cmd).waitFor();
    }

    /**
     * Get all the pid of running app
     * @param packageName Package name of the running app
     * @return List of pid-s
     */
    public static List<Integer> getPIDs(String packageName) {
        List<Integer> pids = new ArrayList<Integer>(3);
        try {
            List<RunningAppProcessInfo> processes = iActivityManager.getRunningAppProcesses();
            for (RunningAppProcessInfo process : processes) {
                for (String pkg : process.pkgList) {
                    if (packageName.equals(pkg)) {
                        pids.add(process.pid);
                        break;
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return pids;
    }

    /**
     * Check if a crashed app is among applications we can switch to.
     * @param processName name of the crashed app
     * @param apps applications we are allowed to switch to
     * @return If this crashed app is among applications we can switch to, return true.
     */
    public static boolean isAppCrash(String processName, ArrayList<ComponentName> apps) {
        for (ComponentName cn : apps) {
            if (processName.contains(cn.getPackageName())) {
                Logger.println("// crash app's package is " + cn.getPackageName());
                return true;
            }
        }
        return false;
    }

    public static boolean stopPackage(String packageName) {
        if (enableStopPackage) {
            int retryCount = 10;
            while (retryCount-- > 0) {
                List<Integer> pids = getPIDs(packageName);
                if (pids.isEmpty()) {
                    return true;
                }
                Logger.println("Stop all packages, retry count " + retryCount);
                try {
                    Logger.println("Try to stop package " + packageName);
                    iActivityManager.forceStopPackage(packageName, UserHandle.myUserId());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * Clear android application user data
     * @param packageName The package name of which data to delete.
     * @return If succeed, return true.
     */
    private static boolean clearPackage(String packageName) {
        try {
            if (!clearPackage) {
                return true;
            }
            int ret = executeCommandAndWaitFor(new String[]{"pm", "clear", packageName});
            return ret == 0;
        } catch (Exception e) {
            Logger.warningFormat("Clear package %s results in error %s", packageName, e);
        }
        return false;
    }

    /**
     * Clear android application user data, if succeed and all requested permissions are
     * granted, revoke them.
     * @param packageName The package name of which data to delete.
     * @param savedPermissions The package name of which permission to revoke.
     * @return If succeed, return true.
     */
    public static boolean clearPackage(String packageName, String[] savedPermissions) {
        return clearPackage(packageName) && grantAllPermission && grantRuntimePermissions(packageName, savedPermissions, "clearing package");
    }

    public static boolean isInputMethod(String packageName) {
        return inputMethodPackages.contains(packageName);
    }

    public static boolean switchToLastInputMethod() {
        try {
            iInputMethodManager.switchToLastInputMethod(null);
            return true;
        } catch (RemoteException e) {
            Logger.warningPrintln("Fail to switch to last input method");
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isAtPhoneLauncher(String topActivity) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homeApps = APIAdapter.queryIntentActivities(packageManager, intent);
        final int NA = homeApps.size();
        for (int a = 0; a < NA; a++) {
            ResolveInfo r = homeApps.get(a);
            String activity = r.activityInfo.name;
            //Logger.println("// the top activity is " + topActivity + ", phone launcher activity is " + activity);
            if (topActivity.equals(activity)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAtPhoneCapture(String topActivity){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> homeApps = APIAdapter.queryIntentActivities(packageManager, intent);
        final int NA = homeApps.size();
        for (int a = 0; a < NA; a++) {
            ResolveInfo r = homeApps.get(a);
            String activity = r.activityInfo.name;
            Logger.println("// the top activity is " + topActivity + ", phone capture activity is " + activity);

            if (topActivity.equals(activity)){
                return true;
            }
        }
        return false;
    }

    public static boolean isAtAppMain(String topActivityClassName, String topActivityPackageName) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        List<ResolveInfo> homeApps = APIAdapter.queryIntentActivities(packageManager, intent);
        final int NA = homeApps.size();
        for (int a = 0; a < NA; a++) {
            ResolveInfo r = homeApps.get(a);
            String packageName = r.activityInfo.applicationInfo.packageName;
            String activity = r.activityInfo.name;
            if (topActivityClassName.equals(activity) && packageName.equals(topActivityPackageName)) {
                return true;
            }
        }
        return false;
    }

    public static void sendIMEActionGo() {
        sendIMEAction(EditorInfo.IME_ACTION_GO);
    }

    public static void sendIMEAction(int actionId) {
        // adb shell am broadcast -a ADB_EDITOR_CODE --ei code 2
        Intent intent = new Intent();
        intent.setAction(IME_EDITORCODE);
        intent.putExtra("code", actionId);
        sendIMEIntent(intent);
    }

    public static boolean sendIMEIntent(Intent intent) {
        try {
            if (checkAndSetInputMethod()) {
                return broadcastIntent(intent);
            }
            return false;
        } finally {
        }
    }

    public static int startActivity(Intent intent) {
        try {
            Object object = APIAdapter.startActivity(iActivityManager, intent);
            if (object == null) {
                if (null != intent && null != intent.getComponent()) {
                    Logger.println("IActivityManager.startActivity failed, execute am start activity");
                    String activity = intent.getComponent().flattenToShortString();
                    executeCommandAndWaitFor(new String[]{"am", "start", "-n", activity});
                }
            }
        } catch (Exception e) {
            Logger.println("Start Activity error: " + e);
            return 0;
        }
        return 1;
    }

    public static int startUri(Intent intent) {
        try {
            Object object = APIAdapter.startActivity(iActivityManager, intent);
            if (object == null) {
                if (null != intent && null != intent.getData()) {
                    Logger.println("IActivityManager.startActivity failed, execute am start uri");
                    String uri = intent.getData().toString();
                    executeCommandAndWaitFor(new String[]{"am", "start", "-d", uri});
                }
            }
        } catch (Exception e) {
            Logger.println("Start Activity error: " + e);
            return 0;
        }
        return 1;
    }

    public static boolean sendChars(int[] chars) {
        Intent intent = new Intent();
        intent.setAction(IME_CHARS);
        intent.putExtra("chars", chars);
        return sendIMEIntent(intent);
    }

    public static boolean sendInputKeyCode(int keycode) {
        Intent intent = new Intent();
        intent.setAction(IME_KEYCODE);
        intent.putExtra("code", keycode);
        return sendIMEIntent(intent);
    }

    private static boolean broadcastIntent(Intent intent) {
        boolean ret = false;
        try {
            APIAdapter.broadcastIntent(iActivityManager, intent);
            ret = true;
        } catch (Exception e) {
            Logger.println("Broadcast Intent error: " + e);
        }
        return ret;
    }

    public static boolean sendText(String text) {
        /*Intent intent = new Intent();
        intent.setAction(IME_MESSAGE);
        intent.putExtra("msg", text);
        return sendIMEIntent(intent);*/
        return injectString(text);
        // sendIMEActionGo();
    }

    private static boolean injectString(String text) {
        if (iInputManager == null) {
            Logger.errorPrintln("** Error: IInputManager not initialized. Cannot inject text.");
            return false;
        }
        if (text == null || text.isEmpty()) {
            return true; // Nothing to inject
        }

        // Get the key character map for the virtual keyboard.
        // KeyCharacterMap.VIRTUAL_KEYBOARD is deprecated from API 34,
        // KeyCharacterMap.DEVICE_ID_VIRTUAL can be used for API 29+
        // For broader compatibility, you might need to load it differently or handle absence.
        KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        if (kcm == null) {
            Logger.errorPrintln("** Error: KeyCharacterMap for virtual keyboard is null.");
            // Fallback: attempt to send events without full KCM mapping (will be very limited)
            // Or, you might decide to fail here.
            // For this example, we'll proceed but acknowledge limitations.
        }

        char[] chars = text.toCharArray();

        for (char c : chars) {
            // Get KeyEvents for the character
            // This is a simplified approach. A full implementation needs to handle all characters,
            // locales, and potentially complex input sequences.
            // KeyCharacterMap.getEvents() is helpful here if KCM is available.
            KeyEvent[] events = null;
            if (kcm != null) {
                events = kcm.getEvents(new char[]{c});
            }

            if (events != null && events.length > 0) {
                for (KeyEvent event : events) {
                    // We need to create new event objects with current time and correct source.
                    // The events from kcm.getEvents() might not have the correct time or device ID.
                    long now = SystemClock.uptimeMillis();
                    KeyEvent newEvent = KeyEvent.changeTimeRepeat(event, now, 0);
                    // Ensure the event source is set, e.g., keyboard
                    // The original event from kcm might have deviceId -1, we should use a valid one.
                    // KeyCharacterMap.VIRTUAL_KEYBOARD is -1.
                    // Using 0 (built-in keyboard) or a specific virtual device ID.
                    if (newEvent.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD) {
                        newEvent = new KeyEvent(now, now, newEvent.getAction(), newEvent.getKeyCode(),
                                0, newEvent.getMetaState(), 0,
                                newEvent.getScanCode(), newEvent.getFlags(), InputDevice.SOURCE_KEYBOARD);
                    } else {
                        newEvent = new KeyEvent(now, now, newEvent.getAction(), newEvent.getKeyCode(),
                                0, newEvent.getMetaState(), newEvent.getDeviceId(),
                                newEvent.getScanCode(), newEvent.getFlags(), InputDevice.SOURCE_KEYBOARD);
                    }


                    if (!injectKeyEvent(newEvent)) {
                        Logger.errorPrintln("Failed to inject key event for char: " + c);
                        // Optionally, stop on first failure or try to continue
                        return false;
                    }
                }
            } else {
                // Fallback for characters not directly mappable by kcm.getEvents() or if kcm is null
                // This is a very basic fallback, only handles simple ASCII.
                // A more robust solution would involve more complex logic or a custom mapping.
                Logger.errorPrintln("No KeyEvent mapping found for char: " + c);
                if (!sendBasicKeyEvents(c)) {
                    Logger.errorPrintln("Failed to inject basic key event for char: " + c);
                }
            }
        }
        return true;
    }

    private static boolean sendBasicKeyEvents(char c) {
        int keyCode = -1;
        int metaState = 0;

        // Simplified mapping (extend as needed, or use a proper library/mapping)
        if (c >= 'a' && c <= 'z') {
            keyCode = KeyEvent.KEYCODE_A + (c - 'a');
        } else if (c >= 'A' && c <= 'Z') {
            keyCode = KeyEvent.KEYCODE_A + (c - 'A');
            metaState = KeyEvent.META_SHIFT_ON;
        } else if (c >= '0' && c <= '9') {
            keyCode = KeyEvent.KEYCODE_0 + (c - '0');
        } else {
            switch (c) {
                case ' ': keyCode = KeyEvent.KEYCODE_SPACE; break;
                case '.': keyCode = KeyEvent.KEYCODE_PERIOD; break;
                case ',': keyCode = KeyEvent.KEYCODE_COMMA; break;
                case '\n': keyCode = KeyEvent.KEYCODE_ENTER; break;
                // Add more common characters here
                default:
                    Logger.errorPrintln("No basic mapping for character: " + c);
                    return false;
            }
        }

        if (keyCode != -1) {
            long now = SystemClock.uptimeMillis();
            KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState,
                    -1, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
            KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState,
                    -1, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);

            return injectKeyEvent(downEvent) && injectKeyEvent(upEvent);
        }
        return false;
    }


    /**
     * Injects a single KeyEvent.
     *
     * @param event The KeyEvent to inject.
     * @return true if successful, false otherwise.
     */
    private static boolean injectKeyEvent(KeyEvent event) {
        if (iInputManager == null) {
            Logger.errorPrintln("** Error: IInputManager not initialized. Cannot inject KeyEvent.");
            return false;
        }
        try {
            // INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH ensures the event is processed
            // before returning, which is generally safer for sequences.
            // Use MODE_ASYNC if you don't need to wait.
            boolean success = iInputManager.injectInputEvent(event, 2);
            if (!success) {
                Logger.errorPrintln("Failed to inject KeyEvent: " + event);
            }
            return success;
        } catch (RemoteException e) {
            Logger.errorPrintln("RemoteException during injectInputEvent: " + e.getMessage());
            e.printStackTrace(); // For more detailed logs
            return false;
        }
    }

    /**
     * Get activity stack through dumpsys
     * @return StackInfo object containing current activity stack
     */
    public static StackInfo getFocusedStack() {
        String[] cmd = new String[]{
                "dumpsys", "activity", "a"
        };

        try {
            String output = Utils.getProcessOutput(cmd);
            String line = null;
            Display currentDisplay = null;
            StackInfo currentStackInfo = null;
            Task currentTask = null;
            ActivityName currentActivityName = null;
            List<Display> displays = new ArrayList<>();
            BufferedReader br = new BufferedReader(new StringReader(output));
            while ((line = br.readLine()) != null) {
                Matcher m = DISPLAY_PATTERN.matcher(line);
                if (m.matches()) {
                    currentDisplay = new Display(Integer.parseInt(m.group(1)));
                    displays.add(currentDisplay);
                    continue;
                }
                m = STACK_PATTERN.matcher(line);
                if (m.matches() && currentDisplay != null) {
                    currentStackInfo = new StackInfo(Integer.parseInt(m.group(1)));

                    currentDisplay.stackInfos.add(currentStackInfo);
                    continue;
                }
                m = TASK_PATTERN.matcher(line);
                if (m.matches() && currentStackInfo != null) {
                    currentTask = new Task(Integer.parseInt(m.group(1)));
                    //Logger.println("// zhangzhao stack.id=" + currentStack.id + ", task.id=" + currentTask.id);
                    currentStackInfo.tasks.add(currentTask);
                    continue;
                }
                m = ACTIVITY_PATTERN.matcher(line);
                if (m.matches() && currentTask != null) {
                    String packageName = m.group(1);
                    String className = m.group(2);
                    if (className.startsWith(".")) {
                        className = packageName + className;
                    }
                    ComponentName comp = new ComponentName(packageName, className);
                    currentActivityName = new ActivityName(comp);
                    currentTask.activityNames.add(currentActivityName);
                    //Logger.println("// zhangzhao stack.id=" + currentStack.id + ", task.id=" + currentTask.id + ", act=" + currentActivity);
                    continue;
                }
                m = FOCUSED_STACK_PATTERN.matcher(line);
                if (m.find() && currentDisplay != null) {
                    currentDisplay.focusedStackId = Integer.parseInt(m.group(1));
                    continue;
                }
                m = DISPLAY_FOCUSED_STACK_PATTERN.matcher(line);
                if (m.find() && currentDisplay != null) {
                    currentDisplay.focusedStackId = Integer.parseInt(m.group(1));
                }
            }
            for (Display d : displays) {
                for (StackInfo s : d.stackInfos) {
                    if (s.id == d.focusedStackId) {
                        return s;
                    }
                }
            }
        } catch (IOException ignore) {
        } catch (InterruptedException ignore) {
        }
        return null;
    }


    public static void main(String[] args) {
        getFocusedStack();
    }

    public static class Display {
        int id;
        int focusedStackId;
        List<StackInfo> stackInfos = new ArrayList<>();

        public Display(int id) {
            this.id = id;
        }
    }

    public static class StackInfo {
        int id;
        List<Task> tasks = new ArrayList<>();

        public StackInfo(int id) {
            this.id = id;
        }

        public List<Task> getTasks() {
            return tasks;
        }

        public void dump() {
            Logger.infoFormat("Stack #%d, sz=%d", id, tasks.size());
            for (Task task : tasks) {
                Logger.infoFormat("- Task #%d, sz=%d", task.id, task.activityNames.size());
                for (ActivityName activityName : task.activityNames) {
                    Logger.infoFormat("  - %s", activityName.activity);
                }
            }
        }
    }

    public static class Task {
        int id;
        List<ActivityName> activityNames = new ArrayList<>();

        public Task(int id) {
            this.id = id;
        }

        public List<ActivityName> getActivityNames() {
            return this.activityNames;
        }
    }

    public static class ActivityName {
        public final ComponentName activity;

        public ActivityName(ComponentName activity) {
            this.activity = activity;
        }

        public ComponentName getActivity() {
            return activity;
        }
    }
}



