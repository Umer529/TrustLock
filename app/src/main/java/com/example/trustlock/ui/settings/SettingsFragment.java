package com.example.trustlock.ui.settings;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.trustlock.databinding.FragmentSettingsBinding;
import com.example.trustlock.receiver.ScreenPactDeviceAdminReceiver;
import com.example.trustlock.ui.approval.WaitingForApprovalDialog;
import com.example.trustlock.util.SessionManager;
import com.google.android.material.snackbar.Snackbar;

public class SettingsFragment extends Fragment {

    private static final String TAG_DIALOG = "uninstall_approval";

    private FragmentSettingsBinding binding;
    private SettingsViewModel       viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        // Show whatever is cached in SharedPreferences immediately (no flicker)
        populateFromSession();

        // Observe fresh profile data loaded from SQLite / Supabase
        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            binding.tvUserName.setText(user.getName()         != null ? user.getName()         : "—");
            binding.tvUserEmail.setText(user.getEmail()       != null ? user.getEmail()        : "—");
            binding.tvGuardianEmail.setText(user.getGuardianEmail() != null
                    ? user.getGuardianEmail() : "—");
        });

        // Load profile (SQLite first, then network refresh)
        viewModel.loadProfile();

        binding.btnActivateAdmin.setOnClickListener(v -> launchAdminActivation());
        binding.btnRequestUninstall.setOnClickListener(v -> viewModel.requestUninstall());

        viewModel.getPendingApproval().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            viewModel.clearPendingApproval();

            WaitingForApprovalDialog dialog = WaitingForApprovalDialog.newInstance(
                    data.requestId,
                    data.guardianEmail,
                    data.description);

            dialog.setOnApprovalResultListener(new WaitingForApprovalDialog.OnApprovalResultListener() {
                @Override public void onApproved() {
                    com.example.trustlock.util.SessionManager.getInstance().clearPendingRequest();
                    deactivateAdminAndUninstall();
                }
                @Override public void onDenied() {
                    com.example.trustlock.util.SessionManager.getInstance().clearPendingRequest();
                    Snackbar.make(requireView(),
                            "Guardian denied the uninstall request", Snackbar.LENGTH_LONG).show();
                }
            });
            dialog.show(getChildFragmentManager(), TAG_DIALOG);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show();
            }
        });

        loadWeeklyStats();
    }

    private void loadWeeklyStats() {
        com.example.trustlock.util.DailyStatsManager statsManager =
                new com.example.trustlock.util.DailyStatsManager(requireContext());
        java.util.Map<String, Long> weeklyUsage = statsManager.getWeeklyTotalUsage(7);

        if (weeklyUsage.isEmpty()) {
            binding.tvNoStatsData.setVisibility(View.VISIBLE);
            binding.weeklyStatsContainer.setVisibility(View.GONE);
            return;
        }

        binding.tvNoStatsData.setVisibility(View.GONE);
        binding.weeklyStatsContainer.setVisibility(View.VISIBLE);
        binding.weeklyStatsContainer.removeAllViews();

        for (int i = 6; i >= 0; i--) {
            String date = com.example.trustlock.util.DailyStatsManager.getDateDaysAgo(i);
            long minutes = weeklyUsage.getOrDefault(date, 0L);

            android.widget.LinearLayout dayRow = new android.widget.LinearLayout(requireContext());
            dayRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            dayRow.setPadding(0, 12, 0, 12);

            android.widget.TextView tvDate = new android.widget.TextView(requireContext());
            tvDate.setText(formatDate(date));
            tvDate.setTextColor(requireContext().getColor(com.example.trustlock.R.color.text_primary));
            tvDate.setTextSize(13);
            tvDate.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            android.widget.TextView tvMinutes = new android.widget.TextView(requireContext());
            tvMinutes.setText(formatMinutes(minutes));
            tvMinutes.setTextColor(requireContext().getColor(
                    minutes > 60 ? com.example.trustlock.R.color.warning
                    : com.example.trustlock.R.color.success));
            tvMinutes.setTextSize(13);
            tvMinutes.setTypeface(android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD));

            dayRow.addView(tvDate);
            dayRow.addView(tvMinutes);
            binding.weeklyStatsContainer.addView(dayRow);

            if (i > 0) {
                android.view.View divider = new android.view.View(requireContext());
                divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(requireContext().getColor(com.example.trustlock.R.color.outline));
                binding.weeklyStatsContainer.addView(divider);
            }
        }
    }

    private String formatDate(String dateStr) {
        try {
            java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.US);
            java.util.Date date = inputFormat.parse(dateStr);
            return outputFormat.format(date);
        } catch (java.text.ParseException e) {
            return dateStr;
        }
    }

    private String formatMinutes(long minutes) {
        if (minutes >= 60) {
            long hours = minutes / 60;
            long mins = minutes % 60;
            return hours + "h " + mins + "m";
        }
        return minutes + "m";
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAdminStatus();
        checkForApprovedUninstall();
        loadWeeklyStats();
    }

    /** If the guardian approved while the dialog was dismissed, complete the uninstall now. */
    private void checkForApprovedUninstall() {
        com.example.trustlock.util.SessionManager session =
                com.example.trustlock.util.SessionManager.getInstance();
        if (com.example.trustlock.models.ApprovalRequest.TYPE_UNINSTALL
                .equals(session.getPendingRequestType())) {
            // The pending request is still there — it hasn't been resolved yet.
            // The service will notify via notification. Nothing to do here.
            // But if it WAS cleared (by the service) before we resumed, the service
            // already showed a notification — also fine.
        }
    }

    private void populateFromSession() {
        SessionManager s = SessionManager.getInstance();
        binding.tvUserName.setText(s.getUserName()      != null ? s.getUserName()      : "—");
        binding.tvUserEmail.setText(s.getUserEmail()    != null ? s.getUserEmail()     : "—");
        binding.tvGuardianEmail.setText(s.getGuardianEmail() != null ? s.getGuardianEmail() : "—");
        updateAdminStatus();
    }

    private void updateAdminStatus() {
        boolean active = isDeviceAdminActive();
        binding.tvAdminStatus.setText(active ? "Active ✓" : "Not active");
        binding.tvAdminStatus.setTextColor(requireContext().getColor(
                active ? com.example.trustlock.R.color.success
                       : com.example.trustlock.R.color.text_secondary));
        binding.btnActivateAdmin.setVisibility(active ? View.GONE : View.VISIBLE);
    }

    private void launchAdminActivation() {
        ComponentName admin = new ComponentName(requireContext(), ScreenPactDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents ScreenPact from being uninstalled without guardian approval.");
        startActivity(intent);
    }

    private void deactivateAdminAndUninstall() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin =
                new ComponentName(requireContext(), ScreenPactDeviceAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin);
        }
        startActivity(new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + requireContext().getPackageName())));
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin =
                new ComponentName(requireContext(), ScreenPactDeviceAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(admin);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
