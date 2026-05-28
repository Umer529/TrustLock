package com.example.trustlock.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trustlock.MainActivity;
import com.example.trustlock.data.LocalRepository;
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
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
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
}
