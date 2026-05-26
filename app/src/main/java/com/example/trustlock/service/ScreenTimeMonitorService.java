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
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.util.BlockedAppsManager;
import com.example.trustlock.util.UsageStatsHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Hilt temporarily disabled — remove Hilt annotations/imports so project can build
public class ScreenTimeMonitorService extends Service {

    private static final String TAG = "ScreenTimeMonitor";
    private static final int NOTIFICATION_ID = 1001;
    private static final long INTERVAL_MS = 60_000L; // 60 seconds

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private Handler handler;
    private Runnable monitorRunnable;
    private ListenerRegistration limitsListener;
    private BlockedAppsManager blockedAppsManager;

    /** Local cache of packageName → limitMinutes, kept fresh by a Firestore listener. */
    private final Map<String, Integer> limitsCache = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        blockedAppsManager = new BlockedAppsManager(this);
        handler = new Handler(Looper.getMainLooper());
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                tick();
                handler.postDelayed(this, INTERVAL_MS);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        startLimitsListener();
        handler.post(monitorRunnable); // First tick immediately, then every 60 s
        return START_STICKY; // System will restart the service if it is killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(monitorRunnable);
        if (limitsListener != null) {
            limitsListener.remove();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private void tick() {
        checkMidnightReset();
        checkUsageAndEnforce();
    }

    /**
     * Compares today's foreground usage against stored limits.
     * Blocks apps that are over their limit; unblocks apps that are under.
     */
    private void checkUsageAndEnforce() {
        if (limitsCache.isEmpty()) return;

        Map<String, Long> usageMap = UsageStatsHelper.getAllAppsUsageToday(this);

        for (Map.Entry<String, Integer> entry : limitsCache.entrySet()) {
            String pkg = entry.getKey();
            int limitMinutes = entry.getValue();
            long usedMinutes = usageMap.containsKey(pkg) ? usageMap.get(pkg) : 0L;

            if (usedMinutes >= limitMinutes) {
                blockedAppsManager.blockApp(pkg, limitMinutes);
                Log.d(TAG, "Blocked: " + pkg + " (" + usedMinutes + "/" + limitMinutes + " min)");
            } else {
                // Unblock if it was previously blocked and usage has dropped (e.g. after midnight reset)
                if (blockedAppsManager.isBlocked(pkg)) {
                    blockedAppsManager.unblockApp(pkg);
                }
            }
        }
    }

    /**
     * On the first tick of a new calendar day, resets all blocked-app state so
     * limits are evaluated fresh from midnight.
     */
    private void checkMidnightReset() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(blockedAppsManager.getLastResetDate())) {
            blockedAppsManager.resetAllAtMidnight();
            blockedAppsManager.setLastResetDate(today);
            Log.d(TAG, "Midnight reset complete for " + today);
        }
    }

    // ─── Firestore limits listener ────────────────────────────────────────────

    /**
     * Keeps limitsCache in sync with Firestore in real time so the monitor always
     * enforces the most up-to-date guardian-approved limits without polling Firestore.
     */
    private void startLimitsListener() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No authenticated user — limits listener not started");
            return;
        }

        limitsListener = db.collection("users")
                .document(user.getUid())
                .collection("appLimits")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Limits listener error", error);
                        return;
                    }
                    if (snapshots == null) return;

                    limitsCache.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        AppLimit limit = doc.toObject(AppLimit.class);
                        if (limit.isActive() && limit.getDailyLimitMinutes() > 0) {
                            limitsCache.put(limit.getPackageName(), limit.getDailyLimitMinutes());
                        }
                    }
                    Log.d(TAG, "Limits cache refreshed: " + limitsCache.size() + " entries");
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
