package com.example.smartcheckin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class Utils {

    private static final String PREFS = "SMART_CHECKIN_PREFS";
    private static final String KEY_CHECKED_OUT = "checked_out";
    private static final String KEY_CHECKOUT_TIME = "checkout_time";
    private static final String KEY_CHECKIN_TIME = "checked_in";

    public static void setCheckedOut(Context context, boolean value) {

        SharedPreferences prefs =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_CHECKED_OUT, value);

        long now = System.currentTimeMillis();

        if (value) {
            // Checkout → save checkout time
            editor.putLong(KEY_CHECKOUT_TIME, now);
            Log.e("UTILS", "Saved CHECKOUT at " + now);
        } else {
            // Check-in → OPTIONAL: save check-in time
            editor.putLong(KEY_CHECKIN_TIME, now);
            Log.e("UTILS", "Saved CHECKIN at " + now);
        }

        editor.apply();    }

    public static boolean isCheckedOut(Context context) {

        SharedPreferences prefs =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        boolean value = prefs.getBoolean(KEY_CHECKED_OUT, false);
        Log.e("UTILS", "Read checked_out = " + value);
        return value;
    }

    public static long getCheckoutTime(Context context) {

        SharedPreferences prefs =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        long time = prefs.getLong(KEY_CHECKOUT_TIME, -1);
        Log.e("UTILS", "Read checkout time = " + time);
        return time;
    }
}
