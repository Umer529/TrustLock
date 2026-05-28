package com.example.trustlock.ui.permissions;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
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
import com.example.trustlock.receiver.ScreenPactDeviceAdminReceiver;

import java.util.Arrays;
import java.util.List;

public class PermissionsActivity extends AppCompatActivity {

    private ActivityPermissionsBinding binding;
    private PermissionsAdapter adapter;
    private List<PermissionItem> permissionItems;

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> recheckAllPermissions());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPermissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        buildPermissionList();

        adapter = new PermissionsAdapter(permissionItems, (item, position) ->
                launchSettingsFor(item.getType()));

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
                        "Prevents uninstall without guardian approval",
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
                // Opens Android's built-in "Activate device administrator" dialog
                // showing exactly which app is requesting admin and why.
                ComponentName adminComponent =
                        new ComponentName(this, ScreenPactDeviceAdminReceiver.class);
                intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Prevents ScreenPact from being uninstalled without guardian approval.");
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
                return isDeviceAdminActive();
            case NOTIFICATIONS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED;
                }
                return true;
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
        // Must use the fully-qualified component name: "pkg/pkg.service.ClassName"
        String serviceId = getPackageName() + "/"
                + "com.example.trustlock.service.AppBlockingAccessibilityService";
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(serviceId);
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, ScreenPactDeviceAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(admin);
    }

    private void updateContinueButton() {
        boolean coreGranted = true;
        for (PermissionItem item : permissionItems) {
            // Device Admin is optional — uninstall protection won't work without it,
            // but the core screen-time features are unaffected.
            if (item.getType() == PermissionItem.Type.DEVICE_ADMIN) continue;
            if (!item.isGranted()) { coreGranted = false; break; }
        }
        binding.btnContinue.setEnabled(coreGranted);
    }
}
