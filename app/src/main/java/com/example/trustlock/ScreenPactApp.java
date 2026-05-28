package com.example.trustlock;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.example.trustlock.util.SessionManager;

public class ScreenPactApp extends Application {

    public static final String CHANNEL_MONITOR = "screen_time_monitor";

    @Override
    public void onCreate() {
        super.onCreate();
        SessionManager.init(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel monitorChannel = new NotificationChannel(
                    CHANNEL_MONITOR,
                    "Screen Time Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            monitorChannel.setDescription("Persistent notification while ScreenPact monitors usage");
            monitorChannel.setShowBadge(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(monitorChannel);
        }
    }
}
