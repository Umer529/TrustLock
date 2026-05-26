package com.example.trustlock.ui.blocked;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.trustlock.databinding.ActivityBlockedBinding;
import com.example.trustlock.util.BlockedAppsManager;
import com.example.trustlock.util.UsageStatsHelper;

public class BlockedAppActivity extends AppCompatActivity {

    public static final String EXTRA_PACKAGE_NAME = "packageName";

    private ActivityBlockedBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBlockedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            finish();
            return;
        }

        populateUi(packageName);

        binding.btnGoBack.setOnClickListener(v -> goToHomeLauncher());

        binding.btnRequestTime.setOnClickListener(v ->
                Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
    }

    /**
     * If the blocked app launches again while this screen is showing (e.g. the user
     * switched away and back), refresh the data instead of stacking a second instance.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName != null) {
            populateUi(packageName);
        }
    }

    private void populateUi(String packageName) {
        PackageManager pm = getPackageManager();

        // App name
        String appName;
        try {
            appName = pm.getApplicationLabel(
                    pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            appName = packageName;
        }
        binding.tvBlockedAppName.setText(appName);

        // App icon via Glide
        try {
            Drawable icon = pm.getApplicationIcon(packageName);
            Glide.with(this).load(icon).into(binding.ivBlockedAppIcon);
        } catch (PackageManager.NameNotFoundException e) {
            binding.ivBlockedAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // Usage vs limit message
        long usedMinutes = UsageStatsHelper.getTodayUsageMinutes(this, packageName);
        int limitMinutes = new BlockedAppsManager(this).getLimitMinutes(packageName);

        String message;
        if (limitMinutes > 0) {
            message = "You've used " + appName + " for " + formatMinutes(usedMinutes)
                    + " today.\nYour daily limit is " + formatMinutes(limitMinutes) + ".";
        } else {
            message = "You've used " + appName + " for "
                    + formatMinutes(usedMinutes) + " today.";
        }
        binding.tvBlockedMessage.setText(message);
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
