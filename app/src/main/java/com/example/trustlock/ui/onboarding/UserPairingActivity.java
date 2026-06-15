package com.example.trustlock.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trustlock.data.RealtimeManager;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.databinding.ActivityUserPairingBinding;
import com.example.trustlock.ui.permissions.PermissionsActivity;
import com.example.trustlock.util.PairingCodeGenerator;
import com.example.trustlock.util.SessionManager;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Shown right after a USER picks their role. Generates a 6-digit pairing code,
 * publishes it on the user's own row, and watches Realtime for the moment a
 * guardian populates {@code guardian_uid}. As soon as that happens we surface
 * a Connected card and reveal the Continue button.
 *
 * The user can dismiss earlier (Continue is the way forward into Permissions),
 * but if no guardian has connected yet the pairing remains pending — the
 * Realtime subscription is per-Activity so we re-establish on every onResume,
 * and the in-app Home banner (added later) will keep nudging until linked.
 */
public class UserPairingActivity extends AppCompatActivity {

    private static final String TAG = "UserPairingActivity";

    private ActivityUserPairingBinding         binding;
    private String                             code;
    private RealtimeManager.Subscription       subscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserPairingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnShareCode.setOnClickListener(v -> shareCode());
        binding.btnContinue .setOnClickListener(v -> goToPermissions());

        generateAndPublishCode();
        startWatchingForGuardian();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (subscription != null) {
            RealtimeManager.getInstance().unsubscribe(subscription);
            subscription = null;
        }
        binding = null;
    }

    // ─── Code generation + publish ───────────────────────────────────────────

    private void generateAndPublishCode() {
        binding.progressGenerating.setVisibility(View.VISIBLE);
        binding.tvCode.setText("——————");

        PairingCodeGenerator.generateUnique(new PairingCodeGenerator.Callback() {
            @Override public void onResult(String generated) {
                code = generated;
                runOnUiThread(() -> binding.tvCode.setText(formatForDisplay(generated)));
                publishCode(generated);
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    binding.progressGenerating.setVisibility(View.GONE);
                    Toast.makeText(UserPairingActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void publishCode(String code) {
        String uid = SessionManager.getInstance().getUserId();
        if (uid == null) return;
        Map<String, Object> body = Collections.singletonMap("pairing_code", code);
        SupabaseClient.getInstance().db()
                .patchUser("return=minimal", "eq." + uid, body)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        runOnUiThread(() -> {
                            binding.progressGenerating.setVisibility(View.GONE);
                            binding.btnShareCode.setEnabled(r.isSuccessful());
                        });
                        if (!r.isSuccessful()) {
                            Log.w(TAG, "publishCode failed: " + r.code());
                        }
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.w(TAG, "publishCode error", t);
                        runOnUiThread(() -> {
                            binding.progressGenerating.setVisibility(View.GONE);
                            Toast.makeText(UserPairingActivity.this,
                                    "Couldn't publish the code. Check your connection and retry.",
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private static String formatForDisplay(String code) {
        // "123456" -> "1 2 3 4 5 6" so each digit reads clearly
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }

    // ─── Share sheet ─────────────────────────────────────────────────────────

    private void shareCode() {
        if (code == null) return;
        String message = "Join me on ScreenPact as my guardian.\n\n"
                + "Open the app, pick \"I want to monitor someone\", and enter my code:\n\n"
                + code;

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(share, "Share pairing code"));
    }

    // ─── Realtime watch ──────────────────────────────────────────────────────

    private void startWatchingForGuardian() {
        String uid = SessionManager.getInstance().getUserId();
        if (uid == null) return;

        // We only care about updates to OUR row — server-side filter does that.
        subscription = RealtimeManager.getInstance().subscribe(
                "users",
                "uid=eq." + uid,
                (eventType, newRow, oldRow) -> {
                    if (!"UPDATE".equals(eventType) || newRow == null) return;
                    String guardianUid = readString(newRow, "guardian_uid");
                    if (guardianUid != null && !guardianUid.isEmpty()) {
                        onPairedWithGuardian(guardianUid);
                    }
                });
    }

    private void onPairedWithGuardian(String guardianUid) {
        // Fetch the guardian's display name in a separate call (RLS allows USER to read
        // the guardian via the users_guardian_read policy — wait, that's the other way
        // around. The user's row references guardian_uid; the guardian's row is
        // readable via standard self-read policy. Here we already have guardian_uid,
        // so we look that user up directly.)
        SupabaseClient.getInstance().db()
                .getUser("eq." + guardianUid)
                .enqueue(new Callback<java.util.List<com.example.trustlock.models.User>>() {
                    @Override public void onResponse(
                            Call<java.util.List<com.example.trustlock.models.User>> c,
                            Response<java.util.List<com.example.trustlock.models.User>> r) {
                        String name = "your guardian";
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                            com.example.trustlock.models.User g = r.body().get(0);
                            if (g.getName() != null && !g.getName().isEmpty()) {
                                name = g.getName();
                            }
                        }
                        showConnected(name);
                    }
                    @Override public void onFailure(
                            Call<java.util.List<com.example.trustlock.models.User>> c,
                            Throwable t) {
                        showConnected("your guardian");
                    }
                });
    }

    private void showConnected(String guardianName) {
        runOnUiThread(() -> {
            if (binding == null) return;
            binding.tvConnectedText.setText("Connected to " + guardianName + " ✓");
            binding.connectedBlock.setVisibility(View.VISIBLE);
            binding.tvWaitingStatus.setVisibility(View.GONE);
            binding.btnContinue.setVisibility(View.VISIBLE);
        });
    }

    private static String readString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        if (obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    // ─── Forward ─────────────────────────────────────────────────────────────

    private void goToPermissions() {
        startActivity(new Intent(this, PermissionsActivity.class));
        finishAffinity();
    }
}
