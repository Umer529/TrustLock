package com.example.trustlock.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * SharedPreferences-backed store for the set of apps that have exceeded their daily limit.
 *
 * Written by ScreenTimeMonitorService, read by AppBlockingAccessibilityService and
 * BlockedAppActivity. All mutating operations copy the Set before writing to avoid the
 * Android bug where modifying a returned Set reference doesn't persist the change.
 */
public class BlockedAppsManager {

    private static final String PREFS_NAME           = "screenpact_blocked_apps";
    private static final String KEY_BLOCKED          = "blocked_packages";
    private static final String KEY_LIMIT_PREFIX     = "limit_";
    private static final String KEY_GRACE_PREFIX     = "grace_until_";
    private static final String KEY_BASELINE_PREFIX  = "baseline_";
    private static final String KEY_LAST_RESET       = "last_reset_date";

    private final SharedPreferences prefs;

    public BlockedAppsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Marks an app as blocked and stores its daily limit for display purposes. */
    public void blockApp(String packageName, int limitMinutes) {
        Set<String> current = getBlockedSet();
        current.add(packageName);
        prefs.edit()
                .putStringSet(KEY_BLOCKED, current)
                .putInt(KEY_LIMIT_PREFIX + packageName, limitMinutes)
                .apply();
    }

    /** Convenience overload — limit stored as 0 when not known. */
    public void blockApp(String packageName) {
        blockApp(packageName, 0);
    }

    public void unblockApp(String packageName) {
        Set<String> current = getBlockedSet();
        if (current.remove(packageName)) {
            prefs.edit()
                    .putStringSet(KEY_BLOCKED, current)
                    .remove(KEY_LIMIT_PREFIX + packageName)
                    .apply();
        }
    }

    public boolean isBlocked(String packageName) {
        return getBlockedSet().contains(packageName);
    }

    /** Returns the daily limit (minutes) stored when the app was blocked. */
    public int getLimitMinutes(String packageName) {
        return prefs.getInt(KEY_LIMIT_PREFIX + packageName, 0);
    }

    /**
     * Grants a temporary reprieve for {@code durationMinutes} minutes.
     * During this window, ScreenTimeMonitorService will not re-block the app.
     */
    public void setGracePeriod(String packageName, int durationMinutes) {
        long graceUntil = System.currentTimeMillis() + (long) durationMinutes * 60_000L;
        prefs.edit().putLong(KEY_GRACE_PREFIX + packageName, graceUntil).apply();
    }

    /** Returns true while an approved grace window is still active. */
    public boolean isInGracePeriod(String packageName) {
        long graceUntil = prefs.getLong(KEY_GRACE_PREFIX + packageName, 0);
        return graceUntil > System.currentTimeMillis();
    }

    /**
     * Snapshot of today's minutes used at the moment the limit was created.
     * Enforcement compares (currentTotalToday - baseline) against the limit so that
     * usage before the limit was applied does NOT count.
     */
    public void setUsageBaseline(String packageName, long minutesUsedNow) {
        prefs.edit().putLong(KEY_BASELINE_PREFIX + packageName, minutesUsedNow).apply();
    }

    public long getUsageBaseline(String packageName) {
        return prefs.getLong(KEY_BASELINE_PREFIX + packageName, 0L);
    }

    public boolean hasUsageBaseline(String packageName) {
        return prefs.contains(KEY_BASELINE_PREFIX + packageName);
    }

    public void clearUsageBaseline(String packageName) {
        prefs.edit().remove(KEY_BASELINE_PREFIX + packageName).apply();
    }

    /**
     * Unblocks every app that is currently blocked but whose package is NOT in
     * {@code activeLimitedPackages}. Call this after refreshing the limits cache so
     * apps whose limits were removed stop being intercepted by the accessibility service.
     */
    public void unblockAppsNotIn(java.util.Set<String> activeLimitedPackages) {
        Set<String> blocked = getBlockedSet();
        for (String pkg : blocked) {
            if (!activeLimitedPackages.contains(pkg)) {
                unblockApp(pkg);
                clearUsageBaseline(pkg);
            }
        }
    }

    /**
     * Clears all blocked apps and their stored limits.
     * Called by ScreenTimeMonitorService on the first tick of a new calendar day.
     */
    public void resetAllAtMidnight() {
        Set<String> blocked = getBlockedSet();
        SharedPreferences.Editor editor = prefs.edit().putStringSet(KEY_BLOCKED, new HashSet<>());
        for (String pkg : blocked) {
            editor.remove(KEY_LIMIT_PREFIX + pkg);
            editor.remove(KEY_GRACE_PREFIX + pkg);
        }
        // Wipe every stored baseline so the new day starts the limit from zero.
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_BASELINE_PREFIX)) editor.remove(key);
        }
        editor.apply();
    }

    /** Returns the stored last-reset date string (yyyy-MM-dd), or empty if never set. */
    public String getLastResetDate() {
        return prefs.getString(KEY_LAST_RESET, "");
    }

    public void setLastResetDate(String dateString) {
        prefs.edit().putString(KEY_LAST_RESET, dateString).apply();
    }

    // SharedPreferences.getStringSet() returns a live reference — always copy before mutating.
    private Set<String> getBlockedSet() {
        return new HashSet<>(prefs.getStringSet(KEY_BLOCKED, new HashSet<>()));
    }
}
