package com.example.trustlock.util;

import android.util.Log;

import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.models.User;

import java.security.SecureRandom;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Generates a 6-digit pairing code that's globally unique across
 * {@code public.users.pairing_code} (which has a UNIQUE constraint).
 *
 * Collision strategy: ask Supabase whether the candidate is free; if yes, hand
 * it back; if not, try again. Capped at a small retry count — 6 digits is one
 * million slots, so collisions are vanishingly rare in practice.
 *
 * Cryptographically secure RNG so the codes aren't guessable, which matters
 * because anyone holding a code can attempt to pair themselves as guardian.
 */
public final class PairingCodeGenerator {

    private static final String TAG          = "PairingCodeGenerator";
    private static final int    MAX_ATTEMPTS = 8;

    public interface Callback {
        void onResult(String code);
        void onError(String message);
    }

    private PairingCodeGenerator() {}

    public static void generateUnique(Callback callback) {
        attempt(callback, 0);
    }

    private static void attempt(Callback callback, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            callback.onError("Could not allocate a unique pairing code. Please try again.");
            return;
        }
        final String candidate = randomSixDigits();

        // GET /rest/v1/users?pairing_code=eq.<candidate>&select=uid
        SupabaseClient.getInstance().db()
                .getUserByPairingCode("eq." + candidate)
                .enqueue(new retrofit2.Callback<List<User>>() {
                    @Override public void onResponse(Call<List<User>> call,
                                                     Response<List<User>> r) {
                        if (!r.isSuccessful()) {
                            // 4xx/5xx — surface and stop; we can't tell whether code is free
                            callback.onError("Server error " + r.code());
                            return;
                        }
                        if (r.body() == null || r.body().isEmpty()) {
                            callback.onResult(candidate);
                        } else {
                            Log.d(TAG, "code collision on attempt " + attempt + ", retrying");
                            attempt(callback, attempt + 1);
                        }
                    }
                    @Override public void onFailure(Call<List<User>> call, Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    private static String randomSixDigits() {
        SecureRandom rng = new SecureRandom();
        int n = rng.nextInt(1_000_000);
        return String.format("%06d", n);
    }
}
