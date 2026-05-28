package com.example.trustlock.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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
import com.example.trustlock.util.BlockedAppsManager;
import com.example.trustlock.util.SessionManager;
import com.example.trustlock.util.UsageStatsHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScreenTimeMonitorService extends Service {

    private static final String TAG             = "ScreenTimeMonitor";
    private static final int    NOTIFICATION_ID = 1001;
    private static final long   INTERVAL_MS     = 60_000L;
    private static final int    LIMITS_REFRESH_TICKS = 5; // refresh limits every 5 minutes

    private Handler          handler;
    private Runnable         monitorRunnable;
    private BlockedAppsManager blockedAppsManager;

    /** packageName → limitMinutes, refreshed from Supabase every LIMITS_REFRESH_TICKS ticks. */
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private void tick() {
        tickCount++;
        if (tickCount % LIMITS_REFRESH_TICKS == 0) {
            refreshLimitsCache();
        }
        checkMidnightReset();
        checkUsageAndEnforce();
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
                if (blockedAppsManager.isBlocked(pkg)) {
                    blockedAppsManager.unblockApp(pkg);
                }
            }
        }
    }

    private void checkMidnightReset() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(blockedAppsManager.getLastResetDate())) {
            blockedAppsManager.resetAllAtMidnight();
            blockedAppsManager.setLastResetDate(today);
            Log.d(TAG, "Midnight reset complete for " + today);
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
                                limitsCache.put(limit.getPackageName(),
                                        limit.getDailyLimitMinutes());
                            }
                        }
                        Log.d(TAG, "Limits cache refreshed: " + limitsCache.size() + " entries");
                    }
                    @Override public void onFailure(Call<List<AppLimit>> call, Throwable t) {
                        Log.e(TAG, "refreshLimitsCache error", t);
                    }
                });
    }

    // ─── Notification ────────────────────────────────────────────────────────

    private Notification buildNotification() {
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, ScreenPactApp.CHANNEL_MONITOR)
                .setContentTitle("ScreenPact Active")
                .setContentText("ScreenPact is protecting your screen time")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build();
    }
}
