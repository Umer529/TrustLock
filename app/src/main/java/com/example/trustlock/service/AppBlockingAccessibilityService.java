package com.example.trustlock.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.trustlock.ui.blocked.BlockedAppActivity;
import com.example.trustlock.util.BlockedAppsManager;

public class AppBlockingAccessibilityService extends AccessibilityService {

    private static final String TAG = "AppBlockingService";

    private BlockedAppsManager blockedAppsManager;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        blockedAppsManager = new BlockedAppsManager(this);
        Log.d(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;

        String pkg = pkgSeq.toString();

        // Never block our own app or the system UI
        if (pkg.equals(getPackageName())) return;
        if (pkg.equals("android")) return;

        if (blockedAppsManager.isBlocked(pkg)) {
            Log.d(TAG, "Blocked app detected in foreground: " + pkg);
            launchBlockScreen(pkg);
        }
    }

    @Override
    public void onInterrupt() {
        // Required override — nothing to clean up
    }

    private void launchBlockScreen(String packageName) {
        Intent intent = new Intent(this, BlockedAppActivity.class);
        intent.putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, packageName);
        // FLAG_ACTIVITY_NEW_TASK required when starting an Activity from a non-Activity context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
}
