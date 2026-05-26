package com.example.trustlock.models;

/** Represents an installed app as returned by PackageManager. Not stored in Firestore. */
public class AppInfo {

    private final String packageName;
    private final String appName;

    public AppInfo(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
    }

    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
}
