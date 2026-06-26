package com.example.trustlock.models;

import com.google.gson.annotations.SerializedName;

/**
 * Daily aggregate row in {@code public.usage_logs} — one row per
 * (user_uid, package_name, log_date).
 */
public class UsageLog {

    @SerializedName("id")
    private String id;

    @SerializedName("user_uid")
    private String userUid;

    @SerializedName("package_name")
    private String packageName;

    @SerializedName("app_name")
    private String appName;

    @SerializedName("minutes_used")
    private int minutesUsed;

    @SerializedName("log_date")
    private String logDate;

    @SerializedName("last_updated")
    private String lastUpdated;

    public UsageLog() {}

    public String getId()          { return id; }
    public void   setId(String v)  { id = v; }

    public String getUserUid()          { return userUid; }
    public void   setUserUid(String v)  { userUid = v; }

    public String getPackageName()          { return packageName; }
    public void   setPackageName(String v)  { packageName = v; }

    public String getAppName()          { return appName; }
    public void   setAppName(String v)  { appName = v; }

    public int  getMinutesUsed()      { return minutesUsed; }
    public void setMinutesUsed(int v) { minutesUsed = v; }

    public String getLogDate()          { return logDate; }
    public void   setLogDate(String v)  { logDate = v; }

    public String getLastUpdated()          { return lastUpdated; }
    public void   setLastUpdated(String v)  { lastUpdated = v; }
}
