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

    private static final Type INTENT_INFO_TYPE = new TypeToken<List<IntentInfo>>() {
    }.getType();

    static {
        File stringFiles = new File("/sdcard/intents.json");
        try {
            JsonReader reader = new JsonReader(new FileReader(stringFiles));
            intentInfos = new Gson().fromJson(reader, INTENT_INFO_TYPE);
        } catch (FileNotFoundException e) {
            intentInfos = null;
            Logger.println("No intents.json file was provided.");
        }
    }

    public static IntentInfo[] getIntentInfos() {
        return intentInfos;
    }

}
