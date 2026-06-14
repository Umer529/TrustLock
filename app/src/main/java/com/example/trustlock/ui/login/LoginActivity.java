package com.example.trustlock.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import android.provider.Settings;

import com.example.trustlock.MainActivity;
import com.example.trustlock.data.LocalRepository;
import com.example.trustlock.ui.permissions.PermissionsActivity;
import com.example.trustlock.data.SupabaseAuthApi;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.data.UserRepository;
import com.example.trustlock.data.local.UserProfileEntity;
import com.example.trustlock.databinding.ActivityLoginBinding;
import com.example.trustlock.util.SessionManager;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> doLogin());
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void doLogin() {
        String email    = getField(binding.etEmail);
        String password = getField(binding.etPassword);

        if (email.isEmpty()) {
            binding.tilEmail.setError("Enter your email");
            return;
        }
        binding.tilEmail.setError(null);

        if (password.isEmpty()) {
            binding.tilPassword.setError("Enter your password");
            return;
        }
        binding.tilPassword.setError(null);

        setLoading(true);

        SupabaseClient.getInstance().auth()
                .signIn("password", new SupabaseAuthApi.SignInRequest(email, password))
                .enqueue(new Callback<SupabaseAuthApi.AuthResponse>() {
                    @Override
                    public void onResponse(Call<SupabaseAuthApi.AuthResponse> call,
                                           Response<SupabaseAuthApi.AuthResponse> response) {
                        if (!response.isSuccessful()
                                || response.body() == null
                                || response.body().user == null) {
                            runOnUiThread(() -> {
                                setLoading(false);
                                binding.tvError.setText("Invalid email or password");
                                binding.tvError.setVisibility(View.VISIBLE);
                            });
                            return;
                        }

                        SupabaseAuthApi.AuthResponse auth = response.body();
                        String uid = auth.user.id;

                        SessionManager session = SessionManager.getInstance();
                        session.setUserId(uid);
                        session.setAccessToken(auth.accessToken);
                        session.setRefreshToken(auth.refreshToken);
                        session.setUserEmail(email);
                        session.setPassword(password);

                        // Fetch full profile to restore name + guardian email
                        new UserRepository().fetchUser(uid, user -> {
                            if (user != null) {
                                session.setUserName(user.getName());
                                session.setGuardianEmail(user.getGuardianEmail());

                                UserProfileEntity profile = new UserProfileEntity();
                                profile.id            = uid;
                                profile.name          = user.getName();
                                profile.email         = user.getEmail();
                                profile.guardianEmail = user.getGuardianEmail();
                                new LocalRepository(LoginActivity.this).saveProfile(profile);
                            }
                            runOnUiThread(() -> {
                                // Go to PermissionsActivity if core permissions are missing;
                                // otherwise skip straight to the main app.
                                Class<?> dest = hasCorePermissions()
                                        ? MainActivity.class
                                        : PermissionsActivity.class;
                                startActivity(new Intent(LoginActivity.this, dest));
                                finishAffinity();
                            });
                        });
                    }

                    @Override
                    public void onFailure(Call<SupabaseAuthApi.AuthResponse> call, Throwable t) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            binding.tvError.setText("Network error. Check your connection.");
                            binding.tvError.setVisibility(View.VISIBLE);
                        });
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnLogin.setText(loading ? "Signing in…" : "Sign In");
        binding.tvError.setVisibility(View.GONE);
    }

    private String getField(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    /**
     * Core = Usage Access + Accessibility + Overlay + Device Admin. All four
     * are needed for blocking + uninstall-protection to work; if any is
     * missing, route the user through PermissionsActivity to grant it.
     */
    private boolean hasCorePermissions() {
        return hasUsageAccess()
                && isAccessibilityEnabled()
                && Settings.canDrawOverlays(this)
                && isDeviceAdminActive();
    }

    private boolean isDeviceAdminActive() {
        android.app.admin.DevicePolicyManager dpm =
                (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        android.content.ComponentName admin = new android.content.ComponentName(
                this,
                com.example.trustlock.receiver.ScreenPactDeviceAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(admin);
    }

    private boolean hasUsageAccess() {
        android.app.AppOpsManager appOps =
                (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
        return mode == android.app.AppOpsManager.MODE_ALLOWED;
    }

    private boolean isAccessibilityEnabled() {
        String serviceId = getPackageName()
                + "/com.example.trustlock.service.AppBlockingAccessibilityService";
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(serviceId);
    }
}
