package com.example.trustlock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
    private static final long   INTERVAL_MS     = 60_000L;
    private static final int    LIMITS_REFRESH_TICKS = 5;

    private static final AtomicInteger APPROVAL_NOTIF_ID = new AtomicInteger(2000);

    private Handler             handler;
    private Runnable            monitorRunnable;
    private BlockedAppsManager  blockedAppsManager;

    private final Map<String, Integer> limitsCache = new HashMap<>();
    private int tickCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        blockedAppsManager = new BlockedAppsManager(this);
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
        refreshLimitsCache();
        handler.post(monitorRunnable);
        return START_STICKY;
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
    }

    private void checkUsageAndEnforce() {
        if (limitsCache.isEmpty()) return;
        Map<String, Long> usageMap = UsageStatsHelper.getAllAppsUsageToday(this);
        for (Map.Entry<String, Integer> entry : limitsCache.entrySet()) {
            String pkg = entry.getKey();
            int limitMinutes = entry.getValue();
            long usedMinutes = usageMap.containsKey(pkg) ? usageMap.get(pkg) : 0L;
            if (usedMinutes >= limitMinutes) {
                if (!blockedAppsManager.isInGracePeriod(pkg)) {
                    blockedAppsManager.blockApp(pkg, limitMinutes);
                    Log.d(TAG, "Blocked: " + pkg + " (" + usedMinutes + "/" + limitMinutes + " min)");
                }
            } else {
                if (blockedAppsManager.isBlocked(pkg)) blockedAppsManager.unblockApp(pkg);
            }
        }
    }

    private void checkMidnightReset() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(blockedAppsManager.getLastResetDate())) {
            blockedAppsManager.resetAllAtMidnight();
            blockedAppsManager.setLastResetDate(today);
        }
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
                                       String packageName, java.util.Map<String, Object> payload) {
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
            showApprovalNotification(
                    approved ? "Uninstall approved" : "Uninstall denied",
                    approved ? "Open ScreenPact to complete the removal"
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

    private void applyLimitFromPayload(java.util.Map<String, Object> payload) {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;

        String pkgName = (String) payload.get("packageName");
        String appName = (String) payload.get("appName");
        Object newMinsObj = payload.get("newLimitMinutes");
        if (pkgName == null || newMinsObj == null) return;

        int newMins = ((Number) newMinsObj).intValue();

        com.example.trustlock.data.UserRepository repo =
                new com.example.trustlock.data.UserRepository();
        repo.updateAppLimit(userId, pkgName, newMins, () -> {
            // Also update Room so the UI is correct on next open
            com.example.trustlock.data.local.AppLimitEntity entity =
                    new com.example.trustlock.data.local.AppLimitEntity();
            entity.userId            = userId;
            entity.packageName       = pkgName;
            entity.appName           = appName;
            entity.dailyLimitMinutes = newMins;
            entity.active            = true;
            new com.example.trustlock.data.LocalRepository(getApplication())
                    .saveLimit(entity);

            showApprovalNotification("Limit change approved",
                    appName + " is now limited to " + formatMinutes(newMins),
                    buildMainIntent());

            // Refresh the monitor's cache so enforcement picks up the new limit
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

        showApprovalNotification("Limit removed",
                "The daily limit for " + appName + " has been removed.",
                buildMainIntent());
    }

    private String formatMinutes(int minutes) {
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

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

    // ─── Supabase limits refresh ──────────────────────────────────────────────

    private void refreshLimitsCache() {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;
        SupabaseClient.getInstance().db()
                .getAppLimits("eq." + userId, "app_name.asc")
                .enqueue(new Callback<List<AppLimit>>() {
                    @Override public void onResponse(Call<List<AppLimit>> call,
                                                     Response<List<AppLimit>> r) {
                        if (!r.isSuccessful() || r.body() == null) return;
                        limitsCache.clear();
                        for (AppLimit limit : r.body()) {
                            if (limit.isActive() && limit.getDailyLimitMinutes() > 0) {
                                limitsCache.put(limit.getPackageName(), limit.getDailyLimitMinutes());
                            }
                        }
                    }
                    @Override public void onFailure(Call<List<AppLimit>> call, Throwable t) {
                        Log.e(TAG, "refreshLimitsCache error", t);
                    }
                });
    }

    // ─── Foreground notification ──────────────────────────────────────────────

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
