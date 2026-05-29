package com.example.trustlock.ui.blocked;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.trustlock.databinding.ActivityBlockedBinding;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.ui.approval.WaitingForApprovalDialog;
import com.example.trustlock.util.ApprovalRequestManager;
import com.example.trustlock.util.BlockedAppsManager;
import com.example.trustlock.util.SessionManager;
import com.example.trustlock.util.UsageStatsHelper;

import java.util.HashMap;
import java.util.Map;

public class BlockedAppActivity extends AppCompatActivity {

    public static final String EXTRA_PACKAGE_NAME = "packageName";
    private static final int   EXTRA_TIME_MINUTES = 30;
    private static final String TAG_DIALOG         = "extra_time_approval";

    private ActivityBlockedBinding binding;
    private BlockedAppsManager     blockedAppsManager;

    private String packageName;
    private String appName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBlockedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        blockedAppsManager = new BlockedAppsManager(this);

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            finish();
            return;
        }

        populateUi(packageName);

        binding.btnGoBack.setOnClickListener(v -> goToHomeLauncher());
        binding.btnRequestTime.setOnClickListener(v -> requestExtraTime());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (pkg != null) {
            packageName = pkg;
            populateUi(packageName);
        }
    }

    private void populateUi(String pkg) {
        PackageManager pm = getPackageManager();

        try {
            appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            appName = pkg;
        }
        binding.tvBlockedAppName.setText(appName);

        try {
            Drawable icon = pm.getApplicationIcon(pkg);
            Glide.with(this).load(icon).into(binding.ivBlockedAppIcon);
        } catch (PackageManager.NameNotFoundException e) {
            binding.ivBlockedAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        long usedMinutes  = UsageStatsHelper.getEffectiveUsageMinutes(this, pkg);
        int  limitMinutes = blockedAppsManager.getLimitMinutes(pkg);

        String message;
        if (limitMinutes > 0) {
            message = "You've used " + appName + " for " + formatMinutes(usedMinutes)
                    + " today.\nYour daily limit is " + formatMinutes(limitMinutes) + ".";
        } else {
            message = "You've used " + appName + " for " + formatMinutes(usedMinutes) + " today.";
        }
        binding.tvBlockedMessage.setText(message);
    }

    private void requestExtraTime() {
        SessionManager session = SessionManager.getInstance();
        String userId        = session.getUserId();
        String guardianEmail = session.getGuardianEmail();

        if (userId == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        if (guardianEmail == null || guardianEmail.isEmpty()) {
            Toast.makeText(this, "No guardian email set", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnRequestTime.setEnabled(false);
        binding.btnRequestTime.setText("Sending…");

        Map<String, Object> payload = new HashMap<>();
        payload.put("packageName",  packageName);
        payload.put("appName",      appName);
        payload.put("extraMinutes", EXTRA_TIME_MINUTES);

        String description = "Allow " + EXTRA_TIME_MINUTES + " more minutes of " + appName;

        new ApprovalRequestManager().createApprovalRequest(
                userId, ApprovalRequest.TYPE_EXTRA_TIME,
                guardianEmail, payload, description,
                requestId -> runOnUiThread(() -> {
                    binding.btnRequestTime.setEnabled(true);
                    binding.btnRequestTime.setText("Request Extra Time");

                    if (requestId == null) {
                        Toast.makeText(this, "Failed to send request. Try again.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Persist so the background service keeps polling if user dismisses the dialog
                    SessionManager.getInstance()
                            .setPendingRequest(requestId, ApprovalRequest.TYPE_EXTRA_TIME, packageName);

                    WaitingForApprovalDialog dialog = WaitingForApprovalDialog.newInstance(
                            requestId, guardianEmail, description);
                    dialog.setOnApprovalResultListener(new WaitingForApprovalDialog.OnApprovalResultListener() {
                        @Override
                        public void onApproved() {
                            SessionManager.getInstance().clearPendingRequest();
                            blockedAppsManager.unblockApp(packageName);
                            blockedAppsManager.setGracePeriod(packageName, EXTRA_TIME_MINUTES);
                            Toast.makeText(BlockedAppActivity.this,
                                    "Approved! You have " + EXTRA_TIME_MINUTES + " extra minutes.",
                                    Toast.LENGTH_LONG).show();
                            goToHomeLauncher();
                        }

                        @Override
                        public void onDenied() {
                            SessionManager.getInstance().clearPendingRequest();
                            Toast.makeText(BlockedAppActivity.this,
                                    "Guardian denied extra time.", Toast.LENGTH_LONG).show();
                        }
                    });

                    if (!isFinishing() && !isDestroyed()) {
                        dialog.show(getSupportFragmentManager(), TAG_DIALOG);
                    }
                }));
    }

    private void goToHomeLauncher() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
        finish();
    }

    private String formatMinutes(long minutes) {
        if (minutes >= 60) {
            return (minutes / 60) + " hr " + (minutes % 60) + " min";
        }
        return minutes + " min";
    }
}
