package com.example.smartcheckin;

public class UserState {

    private boolean checkedIn = false;
    private boolean hasEverCheckedIn = false;

    private long lastCheckInTime = 0;
    private long lastCheckOutTime = 0;

    /* ================= CHECK-IN STATE ================= */

    public boolean isCheckedIn() {
        return checkedIn;
    }

    public void setCheckedIn(boolean checkedIn) {
        this.checkedIn = checkedIn;
    }

    public boolean hasEverCheckedIn() {
        return hasEverCheckedIn;
    }

    public void setHasEverCheckedIn(boolean hasEverCheckedIn) {
        this.hasEverCheckedIn = hasEverCheckedIn;
    }

    /* ================= TIME TRACKING ================= */

    public long getLastCheckInTime() {
        return lastCheckInTime;
    }

    public void setLastCheckInTime(long lastCheckInTime) {
        this.lastCheckInTime = lastCheckInTime;
    }

    public long getLastCheckOutTime() {
        return lastCheckOutTime;
    }

    public void setLastCheckOutTime(long lastCheckOutTime) {
        this.lastCheckOutTime = lastCheckOutTime;
    }
}
