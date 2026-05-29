package com.example.trustlock.util;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.example.trustlock.receiver.ScreenPactDeviceAdminReceiver;

/**
 * Detects when the user disables one of the permissions ScreenPact relies on
 * and sends an alert email to the guardian. Android's security model does not
 * allow apps to *prevent* the user from changing these settings (they are
 * intentionally reserved for the user), so the best we can do is notify.
 *
 * Call {@link #checkAndNotify(Context)} from places that run regularly:
 * the foreground service tick and {@code MainActivity.onResume()}.
 */
public final class PermissionMonitor {

    private static final String PREFS = "screenpact_permission_state";
    private static final String KEY_INITIALIZED   = "_initialized";
    private static final String KEY_USAGE         = "had_usage";
    private static final String KEY_ACCESSIBILITY = "had_accessibility";
    private static final String KEY_OVERLAY       = "had_overlay";
    private static final String KEY_NOTIFICATIONS = "had_notifications";
    private static final String KEY_DEVICE_ADMIN  = "had_device_admin";

    private PermissionMonitor() {}

    /**
     * Compares the current permission state to the last-seen state. For each
     * permission that flipped from granted → revoked, emails the guardian.
     * On first run, just records current state (no notification).
     */
    public static void checkAndNotify(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        boolean firstRun = !prefs.getBoolean(KEY_INITIALIZED, false);

        boolean usage         = hasUsageAccess(app);
        boolean accessibility = isAccessibilityEnabled(app);
        boolean overlay       = Settings.canDrawOverlays(app);
        boolean notifications = hasNotificationPermission(app);
        boolean deviceAdmin   = isDeviceAdminActive(app);

        if (firstRun) {
            prefs.edit()
                    .putBoolean(KEY_INITIALIZED, true)
                    .putBoolean(KEY_USAGE,         usage)
                    .putBoolean(KEY_ACCESSIBILITY, accessibility)
                    .putBoolean(KEY_OVERLAY,       overlay)
                    .putBoolean(KEY_NOTIFICATIONS, notifications)
                    .putBoolean(KEY_DEVICE_ADMIN,  deviceAdmin)
                    .apply();
            return;
        }

        maybeAlert(prefs, KEY_USAGE,         usage,         "Usage Access");
        maybeAlert(prefs, KEY_ACCESSIBILITY, accessibility, "Accessibility Service");
        maybeAlert(prefs, KEY_OVERLAY,       overlay,       "Display Over Other Apps");
        maybeAlert(prefs, KEY_NOTIFICATIONS, notifications, "Notifications");
        maybeAlert(prefs, KEY_DEVICE_ADMIN,  deviceAdmin,   "Device Admin");

        prefs.edit()
                .putBoolean(KEY_USAGE,         usage)
                .putBoolean(KEY_ACCESSIBILITY, accessibility)
                .putBoolean(KEY_OVERLAY,       overlay)
                .putBoolean(KEY_NOTIFICATIONS, notifications)
                .putBoolean(KEY_DEVICE_ADMIN,  deviceAdmin)
                .apply();
    }

    private static void maybeAlert(SharedPreferences prefs, String key,
                                    boolean nowGranted, String permissionLabel) {
        boolean wasGranted = prefs.getBoolean(key, false);
        if (wasGranted && !nowGranted) {
            String guardianEmail = SessionManager.getInstance().getGuardianEmail();
            String description = "Your ward just disabled \"" + permissionLabel
                    + "\" for ScreenPact. Screen-time controls cannot work without it — "
                    + "please remind them to re-enable it in Settings.";
            new ApprovalRequestManager().sendGuardianAlert(guardianEmail, description);
        }
    }

    // ─── Permission probes ────────────────────────────────────────────────────

    private static boolean hasUsageAccess(Context ctx) {
        AppOpsManager appOps =
                (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                ctx.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private static boolean isAccessibilityEnabled(Context ctx) {
        String serviceId = ctx.getPackageName()
                + "/com.example.trustlock.service.AppBlockingAccessibilityService";
        String enabled = Settings.Secure.getString(
                ctx.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(serviceId);
    }

    private static boolean hasNotificationPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(ctx,
                android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isDeviceAdminActive(Context ctx) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return false;
        ComponentName admin = new ComponentName(ctx, ScreenPactDeviceAdminReceiver.class);
        return dpm.isAdminActive(admin);
    }
}
