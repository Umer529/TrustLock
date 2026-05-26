package com.example.trustlock.util;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class UsageStatsHelper {

    private UsageStatsHelper() {}

    public static boolean hasUsagePermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Returns the total foreground minutes for one package today (midnight → now).
     * Returns 0 if the permission is not granted or the app has no usage data.
     */
    public static long getTodayUsageMinutes(Context context, String packageName) {
        if (!hasUsagePermission(context)) return 0;

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long[] window = todayWindow();

        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(window[0], window[1]);
        UsageStats appStats = stats.get(packageName);
        if (appStats == null) return 0;
        return TimeUnit.MILLISECONDS.toMinutes(appStats.getTotalTimeInForeground());
    }

    /**
     * Returns foreground minutes today for every app that has any recorded usage.
     * Key = package name, value = minutes.
     */
    public static Map<String, Long> getAllAppsUsageToday(Context context) {
        Map<String, Long> result = new HashMap<>();
        if (!hasUsagePermission(context)) return result;

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long[] window = todayWindow();

        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(window[0], window[1]);
        for (Map.Entry<String, UsageStats> entry : statsMap.entrySet()) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(entry.getValue().getTotalTimeInForeground());
            if (minutes > 0) {
                result.put(entry.getKey(), minutes);
            }
        }
        return result;
    }

    /** Returns [startOfDayMillis, nowMillis]. */
    private static long[] todayWindow() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new long[]{ cal.getTimeInMillis(), System.currentTimeMillis() };
    }
}
