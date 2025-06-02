package com.android.commands.monkey.provider;

import com.android.commands.monkey.utils.Logger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.List;

public class IntentProvider {

    private static IntentInfo[] intentInfos;

    private static final Type INTENT_INFO_LIST_TYPE = new TypeToken<List<IntentInfo>>(){}.getType();

    static {
        File stringFiles = new File("/sdcard/intents.json");
        try (FileReader fileReader = new FileReader(stringFiles);
             JsonReader reader = new JsonReader(fileReader)) {

            List<IntentInfo> tempList = new Gson().fromJson(reader, INTENT_INFO_LIST_TYPE);

            if (tempList != null) {
                intentInfos = tempList.toArray(new IntentInfo[0]); // Or new IntentInfo[tempList.size()]
            } else {
                intentInfos = null;
                Logger.println("intents.json parsed to null list");
            }
        } catch (FileNotFoundException e) {
            intentInfos = null;
            Logger.println("No intents.json file was provided.");
        } catch (IOException e) {
            intentInfos = new IntentInfo[0];
            Logger.println("IO error reading intents.json: " + e.getMessage());
            e.printStackTrace(); // For debugging
        } catch (Exception e) { // Catch other potential exceptions (e.g., JsonSyntaxException)
            intentInfos = null;
            Logger.println("Error parsing intents.json: " + e.getMessage());
        }
    }

    public static IntentInfo[] getIntentInfos() {
        return intentInfos;
    }

}
