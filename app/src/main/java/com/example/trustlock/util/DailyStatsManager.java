package com.example.trustlock.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tracks daily app usage history in SharedPreferences.
 * Stores usage snapshots at end-of-day so users can see past week's progress.
 */
public class DailyStatsManager {

    private static final String PREFS_NAME = "screenpact_daily_stats";
    private static final String KEY_STATS_PREFIX = "stats_";
    private static final String TAG = "DailyStatsManager";

    private final SharedPreferences prefs;

    public DailyStatsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns today's date as yyyy-MM-dd string. */
    public static String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Returns the date N days ago as yyyy-MM-dd string. */
    public static String getDateDaysAgo(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -daysAgo);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    /**
     * Saves daily usage for an app. Called at end-of-day with the final usage minutes.
     * Stores as JSON: { "packageName": minutes, "packageName2": minutes, ... }
     */
    public void saveDailySnapshot(String date, Map<String, Long> usageMap) {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Long> entry : usageMap.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(KEY_STATS_PREFIX + date, json.toString()).apply();
            Log.d(TAG, "Saved daily snapshot for " + date + ": " + usageMap.size() + " apps");
        } catch (JSONException e) {
            Log.e(TAG, "Error saving daily snapshot", e);
        }
    }

    /**
     * Returns the usage map for a given date, or empty map if no data.
     * Key = package name, value = minutes used that day.
     */
    public Map<String, Long> getDailyUsage(String date) {
        Map<String, Long> result = new HashMap<>();
        String json = prefs.getString(KEY_STATS_PREFIX + date, null);
        if (json == null) return result;

        try {
            JSONObject obj = new JSONObject(json);
            for (int i = 0; i < obj.length(); i++) {
                String key = obj.names().getString(i);
                result.put(key, obj.getLong(key));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading daily usage for " + date, e);
        }
        return result;
    }

    /**
     * Returns usage for a specific app across the past N days.
     * Returns map: date → minutes used that day.
     */
    public Map<String, Long> getAppUsageHistory(String packageName, int daysBack) {
        Map<String, Long> result = new HashMap<>();
        for (int i = 0; i < daysBack; i++) {
            String date = getDateDaysAgo(i);
            Map<String, Long> dayUsage = getDailyUsage(date);
            if (dayUsage.containsKey(packageName)) {
                result.put(date, dayUsage.get(packageName));
            } else {
                result.put(date, 0L);
            }
        }
        return result;
    }

    /**
     * Returns total usage for all apps on a given date.
     */
    public long getTotalDailyUsage(String date) {
        Map<String, Long> dayUsage = getDailyUsage(date);
        long total = 0;
        for (long minutes : dayUsage.values()) {
            total += minutes;
        }
        return total;
    }

    /**
     * Returns total screen time across all apps for the past N days.
     */
    public Map<String, Long> getWeeklyTotalUsage(int daysBack) {
        Map<String, Long> result = new HashMap<>();
        for (int i = 0; i < daysBack; i++) {
            String date = getDateDaysAgo(i);
            result.put(date, getTotalDailyUsage(date));
        }
        return result;
    }
}
