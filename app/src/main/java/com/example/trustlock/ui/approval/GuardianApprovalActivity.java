package com.example.trustlock.ui.approval;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trustlock.R;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.databinding.ActivityGuardianApprovalBinding;
import com.example.trustlock.models.ApprovalRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GuardianApprovalActivity extends AppCompatActivity {

    private ActivityGuardianApprovalBinding binding;
    private String requestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGuardianApprovalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Uri deepLink = getIntent().getData();
        if (deepLink == null) { showError("Invalid link."); return; }

        requestId = deepLink.getQueryParameter("requestId");
        String action = deepLink.getQueryParameter("action");

        if (requestId == null || requestId.isEmpty()) { showError("Missing request ID."); return; }

        if ("approve".equalsIgnoreCase(action)) {
            applyDecision(ApprovalRequest.STATUS_APPROVED);
        } else if ("deny".equalsIgnoreCase(action)) {
            applyDecision(ApprovalRequest.STATUS_DENIED);
        } else {
            loadRequestDetails();
        }
    }

    private void loadRequestDetails() {
        showLoading(true);
        SupabaseClient.getInstance().anonDb()
                .getApprovalRequest("eq." + requestId)
                .enqueue(new Callback<List<ApprovalRequest>>() {
                    @Override public void onResponse(Call<List<ApprovalRequest>> call,
                                                     Response<List<ApprovalRequest>> r) {
                        showLoading(false);
                        if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                            showError("Request not found or already expired.");
                            return;
                        }
                        ApprovalRequest request = r.body().get(0);
                        if (!ApprovalRequest.STATUS_PENDING.equals(request.getStatus())) {
                            boolean wasApproved =
                                    ApprovalRequest.STATUS_APPROVED.equals(request.getStatus());
                            showCompletion(wasApproved ? "Already approved ✓" : "Already denied ✗",
                                    wasApproved);
                            return;
                        }
                        binding.tvRequestDescription.setText(buildDescription(request));
                        binding.layoutContent.setVisibility(View.VISIBLE);
                        binding.btnApprove.setOnClickListener(
                                v -> applyDecision(ApprovalRequest.STATUS_APPROVED));
                        binding.btnDeny.setOnClickListener(
                                v -> applyDecision(ApprovalRequest.STATUS_DENIED));
                    }
                    @Override public void onFailure(Call<List<ApprovalRequest>> call, Throwable t) {
                        showError("Failed to load request.");
                    }
                });
    }

    private void applyDecision(String status) {
        showLoading(true);
        Map<String, String> update = new HashMap<>();
        update.put("status", status);
        SupabaseClient.getInstance().anonDb()
                .updateApprovalStatus("eq." + requestId, update)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        boolean approved = ApprovalRequest.STATUS_APPROVED.equals(status);
                        showCompletion(
                                approved ? "You approved the request ✓" : "You denied the request ✗",
                                approved);
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        showError("Failed to update request.");
                    }
                });
    }

    private void showLoading(boolean loading) {
        binding.progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.layoutContent.setVisibility(View.GONE);
            binding.layoutComplete.setVisibility(View.GONE);
        }
    }

    private void showCompletion(String message, boolean approved) {
        binding.progressLoading.setVisibility(View.GONE);
        binding.layoutContent.setVisibility(View.GONE);
        binding.layoutComplete.setVisibility(View.VISIBLE);
        binding.tvResultMessage.setText(message);
        binding.tvResultMessage.setTextColor(
                getColor(approved ? R.color.success : R.color.error));
    }

    private void showError(String message) {
        binding.progressLoading.setVisibility(View.GONE);
        binding.layoutComplete.setVisibility(View.GONE);
        binding.layoutContent.setVisibility(View.VISIBLE);
        binding.btnApprove.setVisibility(View.GONE);
        binding.btnDeny.setVisibility(View.GONE);
        binding.tvRequestDescription.setText(message);
    }

    private String buildDescription(ApprovalRequest request) {
        Map<String, Object> p = request.getPayload();
        if (p == null) return "An action requires your approval.";
        if (ApprovalRequest.TYPE_CHANGE_LIMIT.equals(request.getType())) {
            Object appName = p.get("appName");
            Object newMin  = p.get("newLimitMinutes");
            Object oldMin  = p.get("currentLimitMinutes");
            if (oldMin != null && ((Number) oldMin).intValue() > 0) {
                return appName + ": change daily limit from " + oldMin + " → " + newMin + " min";
            }
            return appName + ": set " + newMin + "-minute daily limit";
        }
        if (ApprovalRequest.TYPE_UNINSTALL.equals(request.getType())) {
            return "Remove the ScreenPact app";
        }
        if (ApprovalRequest.TYPE_EXTRA_TIME.equals(request.getType())) {
            Object appName    = p.get("appName");
            Object extraMins  = p.get("extraMinutes");
            return "Allow " + extraMins + " more minutes of " + appName;
        }
        return "An action requires your approval.";
    }
}
