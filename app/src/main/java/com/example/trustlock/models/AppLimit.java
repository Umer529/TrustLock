package com.example.trustlock.models;

import com.google.gson.annotations.SerializedName;

public class AppLimit {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("package_name")
    private String packageName;

    @SerializedName("app_name")
    private String appName;

    @SerializedName("daily_limit_minutes")
    private int dailyLimitMinutes;

    @SerializedName("is_active")
    private boolean active;

    public AppLimit() {}

    public AppLimit(String packageName, String appName, int dailyLimitMinutes, boolean active) {
        this.packageName       = packageName;
        this.appName           = appName;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.active            = active;
    }

    public String getUserId()          { return userId; }
    public void   setUserId(String v)  { userId = v; }

    public String getPackageName()          { return packageName; }
    public void   setPackageName(String v)  { packageName = v; }

    public String getAppName()          { return appName; }
    public void   setAppName(String v)  { appName = v; }

    public int  getDailyLimitMinutes()      { return dailyLimitMinutes; }
    public void setDailyLimitMinutes(int v) { dailyLimitMinutes = v; }

    public boolean isActive()          { return active; }
    public void    setActive(boolean v){ active = v; }
}
