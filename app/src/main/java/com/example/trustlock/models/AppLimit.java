package com.example.trustlock.models;

import com.google.firebase.firestore.PropertyName;

public class AppLimit {

    private String packageName;
    private String appName;
    private int dailyLimitMinutes;

    // Firestore's Java deserialization strips the "is" prefix from boolean getters,
    // storing the field as "active" in the database unless we annotate explicitly.
    // @PropertyName keeps the Firestore field name consistent with this model.
    @PropertyName("isActive")
    private boolean active;

    /** Required by Firestore for deserialization. */
    public AppLimit() {}

    public AppLimit(String packageName, String appName, int dailyLimitMinutes, boolean active) {
        this.packageName = packageName;
        this.appName = appName;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.active = active;
    }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public int getDailyLimitMinutes() { return dailyLimitMinutes; }
    public void setDailyLimitMinutes(int dailyLimitMinutes) { this.dailyLimitMinutes = dailyLimitMinutes; }

    @PropertyName("isActive")
    public boolean isActive() { return active; }

    @PropertyName("isActive")
    public void setActive(boolean active) { this.active = active; }
}
