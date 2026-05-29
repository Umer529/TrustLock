package com.example.trustlock.service;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.trustlock.ui.blocked.BlockedAppActivity;
import com.example.trustlock.util.BlockedAppsManager;

public class AppBlockingAccessibilityService extends AccessibilityService {

    private static final String TAG            = "AppBlockingService";
    private static final long   CHECK_INTERVAL = 2_000L;          // 2 seconds — fast & cheap
    private static final long   FG_LOOKBACK_MS = 60 * 60_000L;    // 1 hour window

    private BlockedAppsManager blockedAppsManager;
    private String             currentForegroundPkg;
    private String             lastBlockedLaunch;
    private long               lastBlockedLaunchTs;
    private final Handler      checkHandler = new Handler(Looper.getMainLooper());

    /**
     * Runs every CHECK_INTERVAL ms. Queries UsageStats directly to find the current
     * foreground app — this catches the case where a user is sitting on a single
     * screen and no AccessibilityEvent fires for minutes at a time.
     */
    private final Runnable periodicCheck = new Runnable() {
        @Override public void run() {
            try {
                String fg = getCurrentForegroundApp();
                if (fg != null) currentForegroundPkg = fg;

                if (currentForegroundPkg != null
                        && !currentForegroundPkg.equals(getPackageName())
                        && !currentForegroundPkg.equals("android")
                        && blockedAppsManager.isBlocked(currentForegroundPkg)
                        && !blockedAppsManager.isInGracePeriod(currentForegroundPkg)) {
                    Log.d(TAG, "Periodic check: kicking out " + currentForegroundPkg);
                    launchBlockScreen(currentForegroundPkg);
                }
            } catch (Throwable t) {
                Log.e(TAG, "periodicCheck error", t);
            }
            checkHandler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        blockedAppsManager = new BlockedAppsManager(this);
        checkHandler.removeCallbacks(periodicCheck);
        checkHandler.postDelayed(periodicCheck, CHECK_INTERVAL);
        Log.d(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;

        String pkg = pkgSeq.toString();

        if (pkg.equals(getPackageName()) || pkg.equals("android")) return;

        currentForegroundPkg = pkg;

        if (blockedAppsManager.isBlocked(pkg)
                && !blockedAppsManager.isInGracePeriod(pkg)) {
            Log.d(TAG, "Event blocking foreground app: " + pkg);
            launchBlockScreen(pkg);
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        checkHandler.removeCallbacks(periodicCheck);
    }

    /**
     * Replays UsageEvents to find the package currently in the foreground.
     * Events come in chronological order, so each MOVE_TO_FOREGROUND sets the
     * "current" app and a matching MOVE_TO_BACKGROUND clears it.
     */
    private String getCurrentForegroundApp() {
        UsageStatsManager usm =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents events;
        try {
            events = usm.queryEvents(now - FG_LOOKBACK_MS, now);
        } catch (SecurityException e) {
            return null;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        String currentForeground = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentForeground = event.getPackageName();
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND
                    && event.getPackageName().equals(currentForeground)) {
                currentForeground = null;
            }
        }
        return currentForeground;
    }

    private void launchBlockScreen(String packageName) {
        long now = System.currentTimeMillis();
        // Throttle: don't re-launch the same block screen more than once per second —
        // periodic check fires every 2s, so this just prevents accidental double-tap.
        if (packageName.equals(lastBlockedLaunch) && now - lastBlockedLaunchTs < 1_000L) {
            return;
        }
        lastBlockedLaunch    = packageName;
        lastBlockedLaunchTs  = now;

        Intent intent = new Intent(this, BlockedAppActivity.class);
        intent.putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}
