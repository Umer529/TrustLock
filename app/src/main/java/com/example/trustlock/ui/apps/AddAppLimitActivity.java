package com.example.trustlock.ui.apps;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.trustlock.databinding.ActivityAddAppLimitBinding;
import com.example.trustlock.models.AppInfo;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.viewmodel.AppLimitViewModel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddAppLimitActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT_PACKAGE = "editPackage";
    public static final String EXTRA_EDIT_APP_NAME = "editAppName";
    public static final String EXTRA_CURRENT_LIMIT = "currentLimit";

    private ActivityAddAppLimitBinding binding;
    private AppLimitViewModel viewModel;
    private InstalledAppsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddAppLimitBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AppLimitViewModel.class);

        setupRecyclerView();
        setupSearch();
        observeViewModel();

        binding.btnBack.setOnClickListener(v -> finish());

        // If launched from HomeFragment's edit button, open the sheet immediately
        String editPackage = getIntent().getStringExtra(EXTRA_EDIT_PACKAGE);
        if (editPackage != null) {
            String editAppName = getIntent().getStringExtra(EXTRA_EDIT_APP_NAME);
            int currentLimit = getIntent().getIntExtra(EXTRA_CURRENT_LIMIT, 0);
            openLimitSheet(editPackage, editAppName != null ? editAppName : editPackage, currentLimit);
        }
    }

    private void setupRecyclerView() {
        adapter = new InstalledAppsAdapter(this::onAppSelected);
        binding.rvInstalledApps.setLayoutManager(new LinearLayoutManager(this));
        binding.rvInstalledApps.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void observeViewModel() {
        // Show spinner until apps are loaded
        viewModel.getInstalledApps().observe(this, apps -> {
            binding.progressLoading.setVisibility(View.GONE);
            binding.rvInstalledApps.setVisibility(View.VISIBLE);

            List<AppLimit> limits = viewModel.getAppLimits().getValue();
            Set<String> limitedPackages = buildLimitedSet(limits);
            adapter.setItems(apps, limitedPackages);
        });

        viewModel.getAppLimits().observe(this, limits -> {
            // Refresh the "Limited" badges when limits change
            List<AppInfo> apps = viewModel.getInstalledApps().getValue();
            if (apps != null) {
                adapter.setItems(apps, buildLimitedSet(limits));
            }
        });

        viewModel.getLimitSaved().observe(this, saved -> {
            if (Boolean.TRUE.equals(saved)) {
                showSuccessAndFinish();
            }
        });

        viewModel.getPendingRequestId().observe(this, requestId -> {
            if (requestId != null) {
                showApprovalPendingDialog();
            }
        });

        viewModel.getError().observe(this, errorMsg -> {
            if (errorMsg != null) {
                com.google.android.material.snackbar.Snackbar
                        .make(binding.getRoot(), errorMsg,
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .show();
            }
        });
    }

    private void onAppSelected(AppInfo app) {
        List<AppLimit> limits = viewModel.getAppLimits().getValue();
        int currentLimit = 0;
        if (limits != null) {
            for (AppLimit limit : limits) {
                if (limit.getPackageName().equals(app.getPackageName())) {
                    currentLimit = limit.getDailyLimitMinutes();
                    break;
                }
            }
        }
        openLimitSheet(app.getPackageName(), app.getAppName(), currentLimit);
    }

    private void openLimitSheet(String packageName, String appName, int currentLimitMinutes) {
        SetLimitBottomSheet sheet = SetLimitBottomSheet.newInstance(
                packageName, appName, currentLimitMinutes);
        sheet.show(getSupportFragmentManager(), "set_limit");
    }

    private void showSuccessAndFinish() {
        com.google.android.material.snackbar.Snackbar
                .make(binding.getRoot(), "Limit saved!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show();
        binding.getRoot().postDelayed(this::finish, 800);
    }

    private void showApprovalPendingDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Approval requested")
                .setMessage("A notification has been sent to your guardian. "
                        + "The limit will change once they approve.")
                .setPositiveButton("OK", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private Set<String> buildLimitedSet(List<AppLimit> limits) {
        Set<String> set = new HashSet<>();
        if (limits != null) {
            for (AppLimit l : limits) {
                set.add(l.getPackageName());
            }
        }
        return set;
    }
}
