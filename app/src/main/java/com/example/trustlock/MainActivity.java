package com.example.trustlock;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.trustlock.databinding.ActivityMainBinding;
import com.example.trustlock.receiver.ScreenPactDeviceAdminReceiver;
import com.example.trustlock.service.ScreenTimeMonitorService;
import com.example.trustlock.ui.permissions.PermissionsActivity;
import com.example.trustlock.ui.welcome.WelcomeActivity;
import com.example.trustlock.util.SessionManager;
import com.example.trustlock.util.UsageStatsHelper;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionManager.getInstance().isLoggedIn()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        // If any core permission is missing (Usage, Accessibility, Overlay),
        // route to the permissions screen so the user can grant it.
        if (!hasCorePermissions()) {
            startActivity(new Intent(this, PermissionsActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViewPagerWithNav();
    }

    private boolean hasCorePermissions() {
        return UsageStatsHelper.hasUsagePermission(this)
                && isAccessibilityEnabled()
                && Settings.canDrawOverlays(this);
    }

    private boolean isAccessibilityEnabled() {
        String serviceId = getPackageName()
                + "/com.example.trustlock.service.AppBlockingAccessibilityService";
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(serviceId);
    }

    private void setupViewPagerWithNav() {
        binding.viewPager.setAdapter(new MainPagerAdapter(this));
        // Pre-load adjacent tabs so swipe feels instant
        binding.viewPager.setOffscreenPageLimit(2);

        final int[] tabIds = {
            R.id.homeFragment,
            R.id.appsFragment,
            R.id.settingsFragment
        };

        // BottomNav tap → swipe ViewPager to matching page
        binding.bottomNav.setOnItemSelectedListener(item -> {
            for (int i = 0; i < tabIds.length; i++) {
                if (item.getItemId() == tabIds[i]) {
                    binding.viewPager.setCurrentItem(i, true);
                    return true;
                }
            }
            return false;
        });

        // ViewPager swipe → highlight matching BottomNav item
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.bottomNav.setSelectedItemId(tabIds[position]);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (UsageStatsHelper.hasUsagePermission(this)) {
            startMonitorService();
        }
        checkApprovedUninstall();
        // Catch any permission flips that happened while the foreground service
        // wasn't running (e.g. force-stop, low-memory kill).
        com.example.trustlock.util.PermissionMonitor.checkAndNotify(this);
    }

    /**
     * If the guardian approved an uninstall while the app was backgrounded, the
     * service flagged it in SessionManager. Consume the flag here and trigger
     * the system uninstall dialog so the user can complete the removal.
     */
    private void checkApprovedUninstall() {
        SessionManager session = SessionManager.getInstance();
        if (!session.isUninstallApproved()) return;
        session.setUninstallApproved(false);

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, ScreenPactDeviceAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin);
        }
        startActivity(new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + getPackageName())));
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, ScreenTimeMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
