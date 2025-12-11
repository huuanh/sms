package com.example.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPreferences {
    private static final String PREFS_NAME = "sms_prefs";
    private static final String KEY_FALLBACK_RECEIVER_NUMBER = "fallback_receiver_number";

    private AppPreferences() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveFallbackReceiverNumber(Context context, String receiverNumber) {
        getPrefs(context).edit().putString(KEY_FALLBACK_RECEIVER_NUMBER, receiverNumber).apply();
    }

    public static String getFallbackReceiverNumber(Context context) {
        return getPrefs(context).getString(KEY_FALLBACK_RECEIVER_NUMBER, "");
    }
}
