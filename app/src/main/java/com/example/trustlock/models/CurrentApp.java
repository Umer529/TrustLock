package com.example.trustlock.models;

import com.google.gson.annotations.SerializedName;

/**
 * Singleton row per user — the foreground app reported by the USER's monitor
 * service. The guardian app polls (and Realtime-subscribes to) this row to
 * render the "right now" hero card.
 */
public class CurrentApp {

    @SerializedName("user_uid")
    private String userUid;

    @SerializedName("package_name")
    private String packageName;

    @SerializedName("app_name")
    private String appName;

    /** ISO-8601 timestamp when this app became the foreground app. */
    @SerializedName("since")
    private String since;

    public CurrentApp() {}

    public String getUserUid()          { return userUid; }
    public void   setUserUid(String v)  { userUid = v; }

    public String getPackageName()          { return packageName; }
    public void   setPackageName(String v)  { packageName = v; }

    public String getAppName()          { return appName; }
    public void   setAppName(String v)  { appName = v; }

    public String getSince()          { return since; }
    public void   setSince(String v)  { since = v; }
}
