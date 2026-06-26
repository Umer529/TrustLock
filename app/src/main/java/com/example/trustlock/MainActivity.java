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

import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.data.UserRepository;
import com.example.trustlock.databinding.ActivityMainBinding;
import com.example.trustlock.models.GuardianLink;
import com.example.trustlock.models.Role;
import com.example.trustlock.receiver.ScreenPactDeviceAdminReceiver;
import com.example.trustlock.service.ScreenTimeMonitorService;
import com.example.trustlock.ui.onboarding.RoleSelectionActivity;
import com.example.trustlock.ui.permissions.PermissionsActivity;
import com.example.trustlock.ui.welcome.WelcomeActivity;
import com.example.trustlock.util.GuardianContext;
import com.example.trustlock.util.RoleManager;
import com.example.trustlock.util.SessionManager;
import com.example.trustlock.util.UsageStatsHelper;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        // Role must be chosen before anything else — it determines which nav
        // graph this activity will load (USER vs GUARDIAN, wired up in Step 5).
        if (!RoleManager.getInstance().hasRole()) {
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
            return;
        }

        // Guardians don't need the local monitoring permissions (Accessibility,
        // Overlay, Device Admin, Usage Stats) — they're viewers only. Skip the
        // permissions gate for them.
        Role role = RoleManager.getInstance().getRole();
        if (role == Role.USER && !hasCorePermissions()) {
            startActivity(new Intent(this, PermissionsActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (role == Role.GUARDIAN) {
            setupGuardianNav();
            loadFirstLinkedUser();
        } else {
            setupViewPagerWithNav();
        }
    }

    private boolean hasCorePermissions() {
        return UsageStatsHelper.hasUsagePermission(this)
                && isAccessibilityEnabled()
                && Settings.canDrawOverlays(this)
                && isDeviceAdminActive();
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, ScreenPactDeviceAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(admin);
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

    private void setupGuardianNav() {
        binding.bottomNav.getMenu().clear();
        binding.bottomNav.inflateMenu(R.menu.bottom_nav_guardian_menu);

        binding.viewPager.setAdapter(new GuardianPagerAdapter(this));
        binding.viewPager.setOffscreenPageLimit(4);

        final int[] tabIds = {
            R.id.guardianHomeFragment,
            R.id.liveMonitoringFragment,
            R.id.guardianLimitsFragment,
            R.id.guardianApprovalsFragment,
            R.id.guardianSettingsFragment
        };

        binding.bottomNav.setOnItemSelectedListener(item -> {
            for (int i = 0; i < tabIds.length; i++) {
                if (item.getItemId() == tabIds[i]) {
                    binding.viewPager.setCurrentItem(i, true);
                    return true;
                }
            }
            return false;
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.bottomNav.setSelectedItemId(tabIds[position]);
            }
        });
    }

    /**
     * Auto-pick the first linked user — the Settings tab (Step 5b) will let
     * the guardian switch between multiple linked users.
     */
    private void loadFirstLinkedUser() {
        String guardianUid = SessionManager.getInstance().getUserId();
        if (guardianUid == null) return;

        SupabaseClient.getInstance().db()
                .getGuardianLinks("eq." + guardianUid)
                .enqueue(new Callback<List<GuardianLink>>() {
                    @Override public void onResponse(Call<List<GuardianLink>> call,
                                                     Response<List<GuardianLink>> r) {
                        if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                            GuardianContext.getInstance().setMonitored(null, null);
                            return;
                        }
                        String userUid = r.body().get(0).getUserUid();
                        new UserRepository().fetchUser(userUid, u -> {
                            String name = (u != null && u.getName() != null)
                                    ? u.getName() : userUid;
                            GuardianContext.getInstance().setMonitored(userUid, name);
                        });
                    }
                    @Override public void onFailure(Call<List<GuardianLink>> call, Throwable t) {
                        GuardianContext.getInstance().setMonitored(null, null);
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
