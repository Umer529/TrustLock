package com.example.trustlock.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "app_limits", primaryKeys = {"userId", "packageName"})
public class AppLimitEntity {
    @NonNull public String userId = "";
    @NonNull public String packageName = "";
    public String appName;
    public int dailyLimitMinutes;
    public boolean active;
}
