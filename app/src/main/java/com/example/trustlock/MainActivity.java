package com.example.trustlock;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.trustlock.databinding.ActivityMainBinding;
import com.example.trustlock.service.ScreenTimeMonitorService;
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

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            NavController navController = navHost.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (UsageStatsHelper.hasUsagePermission(this)) {
            startMonitorService();
        }
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
