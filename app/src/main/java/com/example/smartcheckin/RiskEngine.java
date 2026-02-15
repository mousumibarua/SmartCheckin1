
package com.example.smartcheckin;

/**
 * RiskEngine estimates the probability of late arrival
 * based on ETA, remaining time, and user movement.
 */
public class RiskEngine {

    /**
     * Calculates lateness risk score (0–100).
     *
     * @param etaSeconds          Estimated time to reach destination
     * @param timeRemainingSeconds Time left before deadline
     * @param currentSpeedMps     Current speed (m/s)
     */
    public static int calculateRisk(
            double etaSeconds,
            double timeRemainingSeconds,
            float currentSpeedMps
    ) {

        int risk = 0;

        // If ETA exceeds available time → high risk
        if (etaSeconds > timeRemainingSeconds) {
            risk += 40;
        }

        // Speed-based risk
        if (currentSpeedMps < 0.5f) {       // stationary / very slow
            risk += 25;
        } else if (currentSpeedMps < 1.2f) { // slow walking
            risk += 15;
        }

        // Buffer margin
        double ratio = etaSeconds / Math.max(timeRemainingSeconds, 1);

        if (ratio > 1.2) risk += 15;
        if (ratio > 1.5) risk += 20;

        return Math.min(100, risk);
    }
}


