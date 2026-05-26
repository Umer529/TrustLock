package com.example.trustlock;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

// Hilt temporarily disabled for AGP 9 compatibility
// import dagger.hilt.android.HiltAndroidApp;

// @HiltAndroidApp
public class ScreenPactApp extends Application {

    /** Notification channel used by ScreenTimeMonitorService's persistent notification. */
    public static final String CHANNEL_MONITOR = "screen_time_monitor";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel monitorChannel = new NotificationChannel(
                    CHANNEL_MONITOR,
                    "Screen Time Monitor",
                    NotificationManager.IMPORTANCE_LOW  // Silent — just stays in the shade
            );
            monitorChannel.setDescription("Persistent notification while ScreenPact monitors usage");
            monitorChannel.setShowBadge(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(monitorChannel);
        }
    }
}
