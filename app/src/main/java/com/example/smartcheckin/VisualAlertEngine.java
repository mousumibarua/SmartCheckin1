package com.example.smartcheckin;

import android.content.Context;
import android.widget.Toast;

public class VisualAlertEngine {

    public static void showVisualTurn(Context context, String direction) {
        Toast.makeText(context, direction, Toast.LENGTH_SHORT).show();
    }

    public static void showDanger(Context context) {
        Toast.makeText(context, "⚠ HIGH RISK AREA", Toast.LENGTH_LONG).show();
    }
}
