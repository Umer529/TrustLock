package com.example.trustlock.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.databinding.ActivityRoleSelectionBinding;
import com.example.trustlock.models.Role;
import com.example.trustlock.util.RoleManager;
import com.example.trustlock.util.SessionManager;

import java.util.Collections;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Post-registration / pre-permissions screen where the signed-in user chooses
 * whether they're the monitored party (USER) or the monitor (GUARDIAN).
 *
 * Local cache (encrypted) lands first so MainActivity can read it instantly on
 * cold start; we then mirror the choice into Supabase ({@code users.role}).
 * If the network PATCH fails the local choice still stands and we move on —
 * the next backend write will catch it up.
 */
public class RoleSelectionActivity extends AppCompatActivity {

    private static final String TAG = "RoleSelectionActivity";

    private ActivityRoleSelectionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoleSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.cardUser.setOnClickListener(v -> selectRole(Role.USER));
        binding.cardGuardian.setOnClickListener(v -> selectRole(Role.GUARDIAN));
    }

    private void selectRole(Role role) {
        // Cache locally first so cold-start routing works even if we never reach the network.
        RoleManager.getInstance().setRole(role);

        String uid = SessionManager.getInstance().getUserId();
        if (uid == null) {
            // Shouldn't happen — this screen is only shown post-auth — but fail safe.
            Toast.makeText(this, "Not signed in", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        Map<String, Object> body = Collections.singletonMap("role", role.name());
        SupabaseClient.getInstance().db()
                .patchUser("return=minimal", "eq." + uid, body)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        if (!r.isSuccessful()) {
                            Log.w(TAG, "patchUser role failed: " + r.code());
                            // Local cache still wins; we'll move on regardless.
                        }
                        finishToNextStep();
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.w(TAG, "patchUser role error", t);
                        finishToNextStep();
                    }
                });
    }

    private void finishToNextStep() {
        // Each role goes to a different "pair-up first" screen before anything else:
        //   USER     -> publishes a code, waits for guardian to enter it
        //   GUARDIAN -> enters the user's code, creates the link
        Role chosen = com.example.trustlock.util.RoleManager.getInstance().getRole();
        Class<?> next;
        if (chosen == Role.GUARDIAN) {
            next = GuardianPairActivity.class;
        } else {
            next = UserPairingActivity.class;
        }
        startActivity(new Intent(this, next));
        finishAffinity();
    }

    private void setLoading(boolean loading) {
        binding.cardUser.setEnabled(!loading);
        binding.cardGuardian.setEnabled(!loading);
        binding.progressRole.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
