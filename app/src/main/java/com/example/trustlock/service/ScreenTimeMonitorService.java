package com.example.trustlock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.trustlock.MainActivity;
import com.example.trustlock.R;
import com.example.trustlock.ScreenPactApp;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.ui.blocked.BlockedAppActivity;
import com.example.trustlock.util.BlockedAppsManager;
import com.example.trustlock.util.SessionManager;
import com.example.trustlock.util.UsageStatsHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScreenTimeMonitorService extends Service {

    private static final String TAG             = "ScreenTimeMonitor";
    private static final int    NOTIFICATION_ID = 1001;
    private static final long   INTERVAL_MS     = 20_000L;
    private static final int    LIMITS_REFRESH_TICKS = 15;

    private static final AtomicInteger APPROVAL_NOTIF_ID = new AtomicInteger(2000);

    private Handler            handler;
    private Runnable           monitorRunnable;
    private BlockedAppsManager blockedAppsManager;
    private com.example.trustlock.util.DailyStatsManager statsManager;
    private com.example.trustlock.data.LocalRepository    localRepo;

    private final Map<String, Integer> limitsCache = new HashMap<>();
    private int tickCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        blockedAppsManager = new BlockedAppsManager(this);
        statsManager = new com.example.trustlock.util.DailyStatsManager(this);
        localRepo = new com.example.trustlock.data.LocalRepository(this);
        handler = new Handler(Looper.getMainLooper());
        monitorRunnable = new Runnable() {
            @Override public void run() {
                tick();
                handler.postDelayed(this, INTERVAL_MS);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        checkServiceGapAndAlert();
        refreshLimitsCacheThenStart();
        return START_STICKY;
    }

    /**
     * Detects whether the monitor service was killed/frozen/force-stopped for
     * an abnormally long period. The previous tick stamps a heartbeat into
     * SharedPreferences; on every fresh start we compare it to the current
     * clock. A gap larger than {@link #SERVICE_DOWN_THRESHOLD_MS} triggers an
     * alert email to the guardian.
     *
     * We can't *prevent* a user from force-stopping or freezing the app —
     * Android intentionally gives the user final control — but we can report it.
     */
    private static final String PREFS_HEARTBEAT = "screenpact_heartbeat";
    private static final String KEY_LAST_TICK   = "last_tick_ms";
    /** ~5x the normal tick interval. Anything beyond this is a real outage. */
    private static final long   SERVICE_DOWN_THRESHOLD_MS = 5 * 60_000L;

    private void checkServiceGapAndAlert() {
        android.content.SharedPreferences prefs = getSharedPreferences(
                PREFS_HEARTBEAT, MODE_PRIVATE);
        long lastTick = prefs.getLong(KEY_LAST_TICK, 0L);
        long now = System.currentTimeMillis();
        if (lastTick == 0L) return; // First-ever start; nothing to compare to.

        long gap = now - lastTick;
        if (gap < SERVICE_DOWN_THRESHOLD_MS) return;

        String guardianEmail = SessionManager.getInstance().getGuardianEmail();
        long minutes = gap / 60_000L;
        String description = "ScreenPact stopped tracking on your ward's device for about "
                + minutes + " minute" + (minutes == 1 ? "" : "s")
                + ". The app may have been force-stopped or frozen — screen-time "
                + "controls were not active during that time. Please ask your ward "
                + "to reopen ScreenPact.";
        new com.example.trustlock.util.ApprovalRequestManager()
                .sendGuardianAlert(guardianEmail, description);
        Log.w(TAG, "Service was down for " + minutes + " min — guardian notified");
    }

    private void refreshLimitsCacheThenStart() {
        final String userId = SessionManager.getInstance().getUserId();
        if (userId == null) {
            handler.postDelayed(monitorRunnable, INTERVAL_MS);
            return;
        }

        // Step 1: Seed limitsCache from local SQLite immediately. This guarantees
        // enforcement keeps working even with no network — the entire reason for
        // this method existing.
        localRepo.getLimits(userId, cached -> {
            if (cached != null && !cached.isEmpty()) {
                limitsCache.clear();
                for (com.example.trustlock.data.local.AppLimitEntity e : cached) {
                    if (e.active && e.dailyLimitMinutes > 0) {
                        limitsCache.put(e.packageName, e.dailyLimitMinutes);
                    }
                }
                ensureBaselinesForCache();
                Log.d(TAG, "Limits loaded from local cache: " + limitsCache.size());
                // Start enforcing right away off the local data
                handler.post(monitorRunnable);
            }

            // Step 2: Refresh from Supabase in the background. Updates the cache
            // and SQLite if reachable; otherwise we keep using the local copy.
            refreshFromNetwork(userId, cached == null || cached.isEmpty());
        });
    }

    /**
     * Pulls limits from Supabase and updates both the in-memory cache and
     * SQLite. If {@code startMonitorOnResponse} is true we also post the
     * monitor runnable (used by the very-first-install case where the local
     * cache was empty so we hadn't started enforcement yet).
     */
    private void refreshFromNetwork(String userId, boolean startMonitorOnResponse) {
        SupabaseClient.getInstance().db()
                .getAppLimits("eq." + userId, "app_name.asc")
                .enqueue(new Callback<List<AppLimit>>() {
                    @Override public void onResponse(Call<List<AppLimit>> call,
                                                     Response<List<AppLimit>> r) {
                        if (r.isSuccessful() && r.body() != null) {
                            limitsCache.clear();
                            java.util.List<com.example.trustlock.data.local.AppLimitEntity> toCache
                                    = new java.util.ArrayList<>();
                            for (AppLimit limit : r.body()) {
                                if (limit.isActive() && limit.getDailyLimitMinutes() > 0) {
                                    limitsCache.put(limit.getPackageName(),
                                            limit.getDailyLimitMinutes());
                                }
                                // Mirror every row into SQLite so we can read it offline next time
                                com.example.trustlock.data.local.AppLimitEntity e
                                        = new com.example.trustlock.data.local.AppLimitEntity();
                                e.userId            = userId;
                                e.packageName       = limit.getPackageName();
                                e.appName           = limit.getAppName();
                                e.dailyLimitMinutes = limit.getDailyLimitMinutes();
                                e.active            = limit.isActive();
                                toCache.add(e);
                            }
                            if (!toCache.isEmpty()) localRepo.saveLimits(toCache);
                            ensureBaselinesForCache();
                            blockedAppsManager.unblockAppsNotIn(limitsCache.keySet());
                            Log.d(TAG, "Limits refreshed from network: " + limitsCache.size());
                        }
                        if (startMonitorOnResponse) handler.post(monitorRunnable);
                    }
                    @Override public void onFailure(Call<List<AppLimit>> call, Throwable t) {
                        // Offline: keep using whatever's in limitsCache already.
                        Log.w(TAG, "Network limits refresh failed; using local cache", t);
                        if (startMonitorOnResponse) handler.postDelayed(monitorRunnable, INTERVAL_MS);
                    }
                });
    }

    /**
     * For every package in limitsCache that has no baseline yet, snapshot today's
     * current usage as the baseline. This covers limits created before this update
     * was installed and limits added remotely by the guardian — both should also
     * "start now" rather than retroactively count usage since midnight.
     */
    private void ensureBaselinesForCache() {
        Map<String, Long> usageMap = UsageStatsHelper.getAllAppsUsageToday(this);
        for (String pkg : limitsCache.keySet()) {
            if (!blockedAppsManager.hasUsageBaseline(pkg)) {
                long now = usageMap.containsKey(pkg) ? usageMap.get(pkg) : 0L;
                blockedAppsManager.setUsageBaseline(pkg, now);
                Log.d(TAG, "Retroactive baseline for " + pkg + " = " + now);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(monitorRunnable);
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─── Core tick ───────────────────────────────────────────────────────────

    private void tick() {
        tickCount++;
        if (tickCount % LIMITS_REFRESH_TICKS == 0) refreshLimitsCache();
        checkMidnightReset();
        checkUsageAndEnforce();
        checkPendingApproval();
        com.example.trustlock.util.PermissionMonitor.checkAndNotify(this);
        // Stamp the heartbeat. If the service is force-stopped, the gap between
        // this stamp and the next service start will reveal it.
        getSharedPreferences(PREFS_HEARTBEAT, MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_TICK, System.currentTimeMillis())
                .apply();
    }

    private void checkUsageAndEnforce() {
        if (!UsageStatsHelper.hasUsagePermission(this)) {
            Log.w(TAG, "No usage stats permission!");
            return;
        }

        Map<String, Long> usageMap = UsageStatsHelper.getAllAppsUsageToday(this);
        String foreground = getForegroundApp();
        Log.d(TAG, "Checking " + limitsCache.size() + " limits, foreground=" + foreground);

        for (Map.Entry<String, Integer> entry : limitsCache.entrySet()) {
            String pkg         = entry.getKey();
            int    limitMins   = entry.getValue();
            long   totalToday  = usageMap.containsKey(pkg) ? usageMap.get(pkg) : 0L;
            long   baseline    = blockedAppsManager.getUsageBaseline(pkg);
            long   usedMins    = Math.max(0L, totalToday - baseline);

            if (usedMins >= limitMins) {
                if (!blockedAppsManager.isInGracePeriod(pkg)) {
                    blockedAppsManager.blockApp(pkg, limitMins);
                }
            } else {
                if (blockedAppsManager.isBlocked(pkg)) {
                    blockedAppsManager.unblockApp(pkg);
                }
            }
        }

        // Single foreground enforcement: if the current foreground app is blocked
        // (and not in grace), kick it out. Works as the primary path when the
        // Accessibility Service is disabled (overlay permission allows the launch).
        if (foreground != null
                && blockedAppsManager.isBlocked(foreground)
                && !blockedAppsManager.isInGracePeriod(foreground)) {
            launchBlockScreen(foreground);
            Log.d(TAG, "Kicked out foreground app: " + foreground);
        }

        // Unblock any app that is still marked blocked but no longer has a limit set.
        blockedAppsManager.unblockAppsNotIn(limitsCache.keySet());
    }

    private void checkMidnightReset() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String lastReset = blockedAppsManager.getLastResetDate();
        if (!today.equals(lastReset)) {
            // Save yesterday's usage before resetting
            if (!lastReset.isEmpty()) {
                Map<String, Long> yesterdayUsage = UsageStatsHelper.getAllAppsUsageToday(this);
                statsManager.saveDailySnapshot(lastReset, yesterdayUsage);
                Log.d(TAG, "Saved daily snapshot for " + lastReset);
            }
            blockedAppsManager.resetAllAtMidnight();
            blockedAppsManager.setLastResetDate(today);
            Log.d(TAG, "Midnight reset: cleared all blocked apps");
        }
    }

    // ─── Foreground app detection ─────────────────────────────────────────────

    /**
     * Returns the package currently in the foreground (or null if the system /
     * launcher is). Events are returned in chronological order, so we replay them:
     * each MOVE_TO_FOREGROUND sets the current app; a matching MOVE_TO_BACKGROUND
     * clears it. The window is wide enough to catch sessions that began long
     * before the current tick.
     */
    private String getForegroundApp() {
        if (!UsageStatsHelper.hasUsagePermission(this)) return null;
        UsageStatsManager usm =
                (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 60 * 60_000L, now); // last hour
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
        Intent intent = new Intent(this, BlockedAppActivity.class);
        intent.putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    // ─── Background approval polling ─────────────────────────────────────────

    private void checkPendingApproval() {
        SessionManager session = SessionManager.getInstance();
        String requestId = session.getPendingRequestId();
        if (requestId == null) return;

        SupabaseClient.getInstance().db()
                .getApprovalRequest("eq." + requestId)
                .enqueue(new Callback<List<ApprovalRequest>>() {
                    @Override
                    public void onResponse(Call<List<ApprovalRequest>> call,
                                           Response<List<ApprovalRequest>> r) {
                        if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) return;
                        ApprovalRequest req = r.body().get(0);
                        if (ApprovalRequest.STATUS_PENDING.equals(req.getStatus())) return;

                        String type = session.getPendingRequestType();
                        String pkg  = session.getPendingPackageName();
                        session.clearPendingRequest();
                        handleApprovalResult(type, req.getStatus(), pkg, req.getPayload());
                    }
                    @Override public void onFailure(Call<List<ApprovalRequest>> call, Throwable t) {
                        Log.e(TAG, "checkPendingApproval error", t);
                    }
                });
    }

    private void handleApprovalResult(String type, String status,
                                       String packageName, Map<String, Object> payload) {
        boolean approved = ApprovalRequest.STATUS_APPROVED.equals(status);

        if (ApprovalRequest.TYPE_EXTRA_TIME.equals(type)) {
            if (approved && packageName != null) {
                blockedAppsManager.unblockApp(packageName);
                blockedAppsManager.setGracePeriod(packageName, 30);
                showApprovalNotification("Extra time approved!",
                        "You have 30 more minutes for " + getAppName(packageName),
                        buildMainIntent());
            } else {
                showApprovalNotification("Extra time denied",
                        "Your guardian said no.", buildMainIntent());
            }

        } else if (ApprovalRequest.TYPE_UNINSTALL.equals(type)) {
            if (approved) {
                // Persist the approval so MainActivity can trigger the system uninstall
                // dialog when the user taps the notification.
                SessionManager.getInstance().setUninstallApproved(true);
            }
            showApprovalNotification(
                    approved ? "Uninstall approved" : "Uninstall denied",
                    approved ? "You can now uninstall the app safely. Tap to start."
                             : "Your guardian rejected the request.",
                    buildMainIntent());

        } else if (ApprovalRequest.TYPE_CHANGE_LIMIT.equals(type)) {
            if (approved && payload != null) {
                applyLimitFromPayload(payload);
            } else {
                showApprovalNotification("Limit change denied",
                        "Your guardian rejected the request.", buildMainIntent());
            }

        } else if (ApprovalRequest.TYPE_REMOVE_LIMIT.equals(type)) {
            if (approved && packageName != null) {
                removeLimitForPackage(packageName, payload);
            } else {
                showApprovalNotification("Limit removal denied",
                        "Your guardian rejected the request.", buildMainIntent());
            }
        }
    }

    private void applyLimitFromPayload(Map<String, Object> payload) {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;

        String pkgName    = (String) payload.get("packageName");
        String appName    = (String) payload.get("appName");
        Object newMinsObj = payload.get("newLimitMinutes");
        if (pkgName == null || newMinsObj == null) return;
        int newMins = ((Number) newMinsObj).intValue();

        new com.example.trustlock.data.UserRepository()
                .updateAppLimit(userId, pkgName, newMins, () -> {
                    com.example.trustlock.data.local.AppLimitEntity entity =
                            new com.example.trustlock.data.local.AppLimitEntity();
                    entity.userId            = userId;
                    entity.packageName       = pkgName;
                    entity.appName           = appName;
                    entity.dailyLimitMinutes = newMins;
                    entity.active            = true;
                    new com.example.trustlock.data.LocalRepository(getApplication())
                            .saveLimit(entity);

                    showApprovalNotification("Limit updated",
                            appName + " is now limited to " + formatMinutes(newMins),
                            buildMainIntent());
                    limitsCache.put(pkgName, newMins);
                });
    }

    private void removeLimitForPackage(String packageName, Map<String, Object> payload) {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;

        String appName = payload != null && payload.get("appName") != null
                ? (String) payload.get("appName") : getAppName(packageName);

        new com.example.trustlock.data.UserRepository().deleteAppLimit(userId, packageName);
        new com.example.trustlock.data.LocalRepository(getApplication())
                .deleteLimit(userId, packageName);
        blockedAppsManager.unblockApp(packageName);
        blockedAppsManager.clearUsageBaseline(packageName);
        limitsCache.remove(packageName);

        showApprovalNotification("Limit removed",
                "The daily limit for " + appName + " has been removed.",
                buildMainIntent());
    }

    private String formatMinutes(int minutes) {
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    // ─── Supabase limits refresh ──────────────────────────────────────────────

    private void refreshLimitsCache() {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;
        // Reuse the offline-safe path; "false" means don't restart the monitor,
        // it's already running.
        refreshFromNetwork(userId, false);
    }

    // ─── Notifications ────────────────────────────────────────────────────────

    private void showApprovalNotification(String title, String text, PendingIntent tapIntent) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification notif = new NotificationCompat.Builder(this, ScreenPactApp.CHANNEL_APPROVALS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(tapIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        nm.notify(APPROVAL_NOTIF_ID.getAndIncrement(), notif);
    }

    private PendingIntent buildMainIntent() {
        return PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, ScreenPactApp.CHANNEL_MONITOR)
                .setContentTitle("ScreenPact Active")
                .setContentText("Monitoring screen time")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(buildMainIntent())
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build();
    }
}
