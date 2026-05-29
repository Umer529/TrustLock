package com.example.trustlock.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.data.SupabaseDbApi;
import com.example.trustlock.data.SupabaseEdgeApi;
import com.example.trustlock.models.ApprovalRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApprovalRequestManager {

    private static final String TAG              = "ApprovalRequestManager";
    private static final int    POLL_INTERVAL_MS = 3000;

    public interface OnRequestCreatedListener {
        void onCreated(String requestId);
    }

    public interface OnApprovalResultListener {
        void onResult(String status);
    }

    private final SupabaseDbApi   db      = SupabaseClient.getInstance().db();
    private final SupabaseEdgeApi edge    = SupabaseClient.getInstance().edge();
    private final Handler         handler = new Handler(Looper.getMainLooper());
    private       Runnable        pollRunnable;

    /**
     * Creates an approval request in Supabase and emails the guardian a one-click
     * approve/deny link. {@code description} is shown in the email body.
     */
    public void createApprovalRequest(String userId, String type, String guardianEmail,
                                      Map<String, Object> payload, String description,
                                      OnRequestCreatedListener callback) {
        ApprovalRequest request = new ApprovalRequest(
                userId, type, userId, guardianEmail, ApprovalRequest.STATUS_PENDING, payload);

        db.insertApprovalRequest("return=representation", request)
                .enqueue(new Callback<List<ApprovalRequest>>() {
                    @Override public void onResponse(Call<List<ApprovalRequest>> call,
                                                     Response<List<ApprovalRequest>> r) {
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                            String requestId = r.body().get(0).getId();
                            sendGuardianEmail(requestId, guardianEmail, description);
                            callback.onCreated(requestId);
                        } else {
                            Log.e(TAG, "insertApprovalRequest failed: " + r.code());
                            callback.onCreated(null);
                        }
                    }
                    @Override public void onFailure(Call<List<ApprovalRequest>> call, Throwable t) {
                        Log.e(TAG, "insertApprovalRequest error", t);
                        callback.onCreated(null);
                    }
                });
    }

    /** Polls every 3 s until the request reaches APPROVED or DENIED. */
    public void listenForApproval(String requestId, OnApprovalResultListener callback) {
        stopListening();
        pollRunnable = new Runnable() {
            @Override public void run() {
                db.getApprovalRequest("eq." + requestId)
                        .enqueue(new Callback<List<ApprovalRequest>>() {
                            @Override public void onResponse(Call<List<ApprovalRequest>> call,
                                                             Response<List<ApprovalRequest>> r) {
                                if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                                    return;
                                }
                                String status = r.body().get(0).getStatus();
                                if (ApprovalRequest.STATUS_APPROVED.equals(status)
                                        || ApprovalRequest.STATUS_DENIED.equals(status)) {
                                    callback.onResult(status);
                                } else {
                                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                                }
                            }
                            @Override public void onFailure(Call<List<ApprovalRequest>> call,
                                                            Throwable t) {
                                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                            }
                        });
            }
        };
        handler.post(pollRunnable);
    }

    public void cancelRequest(String requestId) {
        stopListening();
        Map<String, String> update = new HashMap<>();
        update.put("status", ApprovalRequest.STATUS_DENIED);
        db.updateApprovalStatus("eq." + requestId, update).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {}
            @Override public void onFailure(Call<Void> c, Throwable t) {}
        });
    }

    public void stopListening() {
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void sendGuardianEmail(String requestId, String guardianEmail, String description) {
        if (guardianEmail == null || guardianEmail.isEmpty()) return;

        String wardName = SessionManager.getInstance().getUserName();
        if (wardName == null || wardName.isEmpty()) wardName = "Your ward";

        SupabaseEdgeApi.SendEmailRequest req = new SupabaseEdgeApi.SendEmailRequest(
                requestId, guardianEmail, wardName, description);

        edge.sendApprovalEmail(req).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                if (!r.isSuccessful()) Log.e(TAG, "sendEmail failed: " + r.code());
            }
            @Override public void onFailure(Call<Void> c, Throwable t) {
                Log.e(TAG, "sendEmail error", t);
            }
        });
    }

    /**
     * Sends an alert-only email to the guardian (no approve/deny buttons, no DB row).
     * Use for one-way notifications like "ward disabled accessibility".
     */
    public void sendGuardianAlert(String guardianEmail, String description) {
        if (guardianEmail == null || guardianEmail.isEmpty()) return;

        String wardName = SessionManager.getInstance().getUserName();
        if (wardName == null || wardName.isEmpty()) wardName = "Your ward";

        SupabaseEdgeApi.SendEmailRequest req =
                SupabaseEdgeApi.SendEmailRequest.alert(guardianEmail, wardName, description);

        edge.sendApprovalEmail(req).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                if (!r.isSuccessful()) Log.e(TAG, "sendAlert failed: " + r.code());
            }
            @Override public void onFailure(Call<Void> c, Throwable t) {
                Log.e(TAG, "sendAlert error", t);
            }
        });
    }
}
