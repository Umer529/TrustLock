package com.example.trustlock.ui.permissions;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.trustlock.MainActivity;
import com.example.trustlock.R;
import com.example.trustlock.databinding.ActivityPermissionsBinding;

import java.util.Arrays;
import java.util.List;

public class PermissionsActivity extends AppCompatActivity {

    private ActivityPermissionsBinding binding;
    private PermissionsAdapter adapter;
    private List<PermissionItem> permissionItems;

    // Tracks which permission row launched the system settings screen
    private int pendingGrantPosition = -1;

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Re-check all permissions when returning from Settings
                recheckAllPermissions();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPermissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        buildPermissionList();

        adapter = new PermissionsAdapter(permissionItems, (item, position) -> {
            pendingGrantPosition = position;
            launchSettingsFor(item.getType());
        });

        binding.rvPermissions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPermissions.setAdapter(adapter);

        binding.btnContinue.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        recheckAllPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        recheckAllPermissions();
    }

    private void buildPermissionList() {
        permissionItems = Arrays.asList(
                new PermissionItem(
                        PermissionItem.Type.USAGE_ACCESS,
                        "Usage Access",
                        "Lets ScreenPact see how long you spend in each app",
                        android.R.drawable.ic_menu_recent_history),
                new PermissionItem(
                        PermissionItem.Type.ACCESSIBILITY,
                        "Accessibility Service",
                        "Lets ScreenPact detect when a time-limited app is opened",
                        android.R.drawable.ic_menu_view),
                new PermissionItem(
                        PermissionItem.Type.DEVICE_ADMIN,
                        "Device Admin",
                        "Prevents ScreenPact from being uninstalled without guardian approval",
                        android.R.drawable.ic_lock_lock),
                new PermissionItem(
                        PermissionItem.Type.NOTIFICATIONS,
                        "Notifications",
                        "Sends alerts when limits are reached or requests need approval",
                        android.R.drawable.ic_dialog_info)
        );
    }

    private void launchSettingsFor(PermissionItem.Type type) {
        Intent intent;
        switch (type) {
            case USAGE_ACCESS:
                intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                break;
            case ACCESSIBILITY:
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
            case DEVICE_ADMIN:
                // DeviceAdminHelper.requestAdminActivation() added in Step 8
                intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                break;
            case NOTIFICATIONS:
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                break;
            default:
                return;
        }
        settingsLauncher.launch(intent);
    }

    private void recheckAllPermissions() {
        for (int i = 0; i < permissionItems.size(); i++) {
            permissionItems.get(i).setGranted(isGranted(permissionItems.get(i).getType()));
            adapter.notifyItemChanged(i);
        }
        updateContinueButton();
    }

    private boolean isGranted(PermissionItem.Type type) {
        switch (type) {
            case USAGE_ACCESS:
                return hasUsageStatsPermission();
            case ACCESSIBILITY:
                return isAccessibilityServiceEnabled();
            case DEVICE_ADMIN:
                // Checked properly in Step 8 via DeviceAdminHelper
                return false;
            case NOTIFICATIONS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED;
                }
                return true; // Pre-13 notifications don't need runtime permission
        }
        return false;
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceId = getPackageName() + "/.service.AppBlockingAccessibilityService";
        String settingValue = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return settingValue != null && settingValue.contains(serviceId);
    }

    private void updateContinueButton() {
        boolean allGranted = true;
        for (PermissionItem item : permissionItems) {
            // Device Admin is checked separately — skip it for the gate for now
            if (item.getType() == PermissionItem.Type.DEVICE_ADMIN) continue;
            if (!item.isGranted()) {
                allGranted = false;
                break;
            }
        }
        binding.btnContinue.setEnabled(allGranted);
    }
}
