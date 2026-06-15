package com.example.trustlock.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trustlock.MainActivity;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.data.SupabaseDbApi;
import com.example.trustlock.databinding.ActivityGuardianPairBinding;
import com.example.trustlock.models.GuardianLink;
import com.example.trustlock.models.User;
import com.example.trustlock.util.SessionManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Guardian-side pairing screen. Takes a 6-digit code, resolves it to a user
 * via the pairing_code lookup, then writes two records in sequence:
 *   1. INSERT into guardian_links  (guardian_uid, user_uid)
 *   2. PATCH  the user's row  (guardian_uid)
 *
 * On success, forwards to MainActivity — which will load the Guardian nav
 * graph once Step 5 lands.
 */
public class GuardianPairActivity extends AppCompatActivity {

    private static final String TAG = "GuardianPairActivity";

    private ActivityGuardianPairBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGuardianPairBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnConnect.setOnClickListener(v -> attemptConnect());

        binding.etCode.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                // Hide any stale error as soon as the user starts editing.
                binding.tvError.setVisibility(View.GONE);
            }
        });
    }

    private void attemptConnect() {
        String code = binding.etCode.getText() != null
                ? binding.etCode.getText().toString().trim() : "";

        if (code.length() != 6 || !code.matches("\\d{6}")) {
            showError("Pairing codes are 6 digits.");
            return;
        }

        String guardianUid = SessionManager.getInstance().getUserId();
        if (guardianUid == null) {
            showError("Not signed in. Please log back in and try again.");
            return;
        }

        setLoading(true);

        // 1. Look up the user who owns this pairing code.
        SupabaseDbApi db = SupabaseClient.getInstance().db();
        db.getUserByPairingCode("eq." + code).enqueue(new Callback<List<User>>() {
            @Override public void onResponse(Call<List<User>> call, Response<List<User>> r) {
                if (!r.isSuccessful()) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showError("Server error " + r.code() + ". Please try again.");
                    });
                    return;
                }
                List<User> body = r.body();
                if (body == null || body.isEmpty()) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showError("Invalid code, please check with your user.");
                    });
                    return;
                }
                User target = body.get(0);
                if (guardianUid.equals(target.getUid())) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showError("That's your own code. Ask the person you want to monitor for theirs.");
                    });
                    return;
                }
                createLink(guardianUid, target);
            }
            @Override public void onFailure(Call<List<User>> call, Throwable t) {
                Log.w(TAG, "lookup error", t);
                runOnUiThread(() -> {
                    setLoading(false);
                    showError("Network error. Check your connection.");
                });
            }
        });
    }

    private void createLink(String guardianUid, User target) {
        SupabaseDbApi db = SupabaseClient.getInstance().db();
        GuardianLink link = new GuardianLink(guardianUid, target.getUid());

        db.insertGuardianLink("return=minimal", link)
                .enqueue(new Callback<List<GuardianLink>>() {
                    @Override public void onResponse(Call<List<GuardianLink>> call,
                                                     Response<List<GuardianLink>> r) {
                        // Duplicate links return 409; treat that as "already linked, fine"
                        // and just move on to patching the user row.
                        if (r.isSuccessful() || r.code() == 409) {
                            patchUserGuardian(guardianUid, target);
                        } else {
                            runOnUiThread(() -> {
                                setLoading(false);
                                showError("Couldn't create link (" + r.code() + ").");
                            });
                        }
                    }
                    @Override public void onFailure(Call<List<GuardianLink>> call, Throwable t) {
                        Log.w(TAG, "insertGuardianLink error", t);
                        runOnUiThread(() -> {
                            setLoading(false);
                            showError("Network error. Check your connection.");
                        });
                    }
                });
    }

    private void patchUserGuardian(String guardianUid, User target) {
        Map<String, Object> body = Collections.singletonMap("guardian_uid", guardianUid);
        SupabaseClient.getInstance().db()
                .patchUser("return=minimal", "eq." + target.getUid(), body)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        if (!r.isSuccessful()) {
                            // The guardian_links row already exists at this point, so
                            // we still consider the pair successful; the user's row will
                            // be reconciled next time it's read.
                            Log.w(TAG, "patchUserGuardian failed: " + r.code());
                        }
                        runOnUiThread(() -> finishToGuardianHome());
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.w(TAG, "patchUserGuardian error", t);
                        runOnUiThread(() -> finishToGuardianHome());
                    }
                });
    }

    private void finishToGuardianHome() {
        // MainActivity reads the cached role and (in Step 5) will pick the
        // guardian nav graph. For now it'll show the existing user UI even
        // for guardians — that's OK; the link is created and visible in DB.
        startActivity(new Intent(this, MainActivity.class));
        finishAffinity();
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        binding.btnConnect.setEnabled(!loading);
        binding.etCode.setEnabled(!loading);
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
