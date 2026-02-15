package com.example.smartcheckin;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;

public class RouteResultDialog extends DialogFragment {

    private List<RouteScore> routes = new ArrayList<>();
    private LinearLayout container;

    // ✅ REQUIRED: no-arg constructor
    public RouteResultDialog() {
    }

    // ✅ Called AFTER dialog is shown
    public void setRoutes(List<RouteScore> routes) {
        this.routes = routes;
        if (container != null) {
            populateRoutes();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_routes);

        container = dialog.findViewById(R.id.routeContainer);

        // In case routes are already available
        populateRoutes();

        return dialog;
    }

    /* ---------------- UI POPULATION ---------------- */

    private void populateRoutes() {

        if (container == null || routes == null) return;

        container.removeAllViews();

        for (RouteScore r : routes) {

            TextView tv = new TextView(getContext());
            tv.setPadding(24, 20, 24, 20);
            tv.setTextSize(14);

            tv.setText(
                    "Mode: " + r.route.mode.name() + "\n" +
                            "Distance: " + String.format("%.2f", r.distanceKm) + " km\n" +
                            "ETA: " + String.format("%.1f", r.etaMinutes) + " mins\n" +
                            "Risk: " + r.riskLevel
            );

            // Risk-based color
            if ("GREEN".equals(r.riskLevel)) {
                tv.setBackgroundColor(0xFFDFF5E1);
            } else if ("YELLOW".equals(r.riskLevel)) {
                tv.setBackgroundColor(0xFFFFF4CC);
            } else {
                tv.setBackgroundColor(0xFFFFE0E0);
            }

            tv.setOnClickListener(v -> {
                if (getActivity() instanceof RouteSelectionListener) {
                    ((RouteSelectionListener) getActivity())
                            .onRouteSelected(r);
                }
                dismiss();
            });

            container.addView(tv);
        }
    }
}
