package com.android.commands.monkey.provider;

import android.content.ComponentName;
import android.content.pm.ComponentInfo;

public class IntentInfo {
    private String activityName;
    private ComponentName componentName;
    private String data;
    private int priority;

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public ComponentName getComponentName() {
        return componentName;
    }

    public void setComponentName(ComponentName componentInfo) {
        this.componentName = componentInfo;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
