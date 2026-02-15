package com.example.smartcheckin;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {

    private static final String PREF_NAME = "smartcheckin_prefs";
    private static final String KEY_PROFILE = "accessibility_profile";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    /* ---------------- SAVE PROFILE ---------------- */

    public static void saveProfile(Context context,
                                   AccessibilityProfile profile) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME,
                        Context.MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_PROFILE, profile.name())
                .apply();
    }

    /* ---------------- GET PROFILE ---------------- */

    public static AccessibilityProfile getProfile(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME,
                        Context.MODE_PRIVATE);

        String saved =
                prefs.getString(KEY_PROFILE,
                        AccessibilityProfile.STANDARD.name());

        return AccessibilityProfile.valueOf(saved);
    }

    /* ---------------- FIRST LAUNCH ---------------- */

    public static boolean isFirstLaunch(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME,
                        Context.MODE_PRIVATE);

        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public static void setFirstLaunchCompleted(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME,
                        Context.MODE_PRIVATE);

        prefs.edit()
                .putBoolean(KEY_FIRST_LAUNCH, false)
                .apply();
    }
}
