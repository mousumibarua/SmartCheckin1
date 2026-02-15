package com.example.smartcheckin;

import android.content.Context;
import android.content.SharedPreferences;

public class AccessibilityManager {

    private static final String PREF_NAME = "accessibility_prefs";
    private static final String KEY_PROFILE = "profile";

    public static void saveProfile(Context context, AccessibilityProfile profile) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PROFILE, profile.name()).apply();
    }

    public static AccessibilityProfile getProfile(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_PROFILE, AccessibilityProfile.STANDARD.name());
        return AccessibilityProfile.valueOf(value);
    }
}
