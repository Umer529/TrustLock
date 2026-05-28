package com.example.trustlock.receiver;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.trustlock.MainActivity;
import com.example.trustlock.R;
import com.example.trustlock.ScreenPactApp;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.util.SessionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScreenPactDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "DeviceAdminReceiver";

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "⚠ To remove ScreenPact, please use the \"Request to Uninstall\" option "
                + "inside the app so your guardian can approve it first. "
                + "Removing admin access here will notify your guardian immediately.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.w(TAG, "Device admin disabled — notifying guardian");

        // Show a local notification immediately
        showLocalNotification(context);

        // Attempt to notify guardian via an approval request marked as a bypass attempt
        notifyGuardianOfBypass(context);
    }

    private void showLocalNotification(Context context) {
        PendingIntent tapIntent = PendingIntent.getActivity(
                context, 0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(3001,
                new NotificationCompat.Builder(context, ScreenPactApp.CHANNEL_APPROVALS)
                        .setSmallIcon(R.drawable.ic_shield)
                        .setContentTitle("⚠ Device Admin removed")
                        .setContentText(
                                "ScreenPact admin access was removed. Guardian has been notified.")
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(tapIntent)
                        .build());
    }

    private void notifyGuardianOfBypass(Context context) {
        SessionManager session = SessionManager.getInstance();
        String userId        = session.getUserId();
        String guardianEmail = session.getGuardianEmail();
        String wardName      = session.getUserName();
        if (userId == null || guardianEmail == null) return;
        if (wardName == null || wardName.isEmpty()) wardName = "Your ward";

        final String finalWardName = wardName;
        String description = finalWardName + " removed the Device Admin access from ScreenPact. "
                + "ScreenPact can now be uninstalled without your approval.";

        // Send guardian email (alert mode — no approve/deny buttons)
        com.example.trustlock.data.SupabaseEdgeApi.SendEmailRequest emailReq =
                com.example.trustlock.data.SupabaseEdgeApi.SendEmailRequest
                        .alert(guardianEmail, finalWardName, description);

        SupabaseClient.getInstance().edge()
                .sendApprovalEmail(emailReq)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c, Response<Void> r) {
                        Log.d(TAG, "Guardian alert email sent: " + r.code());
                    }
                    @Override public void onFailure(Call<Void> c, Throwable t) {
                        Log.e(TAG, "Failed to send guardian alert email", t);
                    }
                });

        // Also record the event in the DB
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "User manually removed Device Admin to bypass guardian controls");

        ApprovalRequest req = new ApprovalRequest(
                userId, "ADMIN_DISABLED", userId, guardianEmail,
                ApprovalRequest.STATUS_DENIED, payload);

        SupabaseClient.getInstance().db()
                .insertApprovalRequest("return=representation", req)
                .enqueue(new Callback<List<ApprovalRequest>>() {
                    @Override public void onResponse(Call<List<ApprovalRequest>> c,
                                                     Response<List<ApprovalRequest>> r) {
                        Log.d(TAG, "Admin-disabled event recorded: " + r.code());
                    }
                    @Override public void onFailure(Call<List<ApprovalRequest>> c, Throwable t) {
                        Log.e(TAG, "Failed to record admin-disabled event", t);
                    }
                });
    }
}
