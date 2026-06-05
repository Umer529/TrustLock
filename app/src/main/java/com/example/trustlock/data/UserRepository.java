package com.example.trustlock.data;

import android.util.Log;

import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository {

    private static final String TAG = "UserRepository";
    private final SupabaseDbApi db = SupabaseClient.getInstance().db();

    public interface Callback1<T> {
        void onResult(T result);
    }

    public void saveUser(User user) {
        db.insertUser("return=minimal", user).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (!r.isSuccessful()) Log.e(TAG, "saveUser failed: " + r.code());
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "saveUser error", t);
            }
        });
    }

    public void saveAppLimit(String userId, AppLimit limit) {
        saveAppLimit(userId, limit, null);
    }

    public void saveAppLimit(String userId, AppLimit limit, Runnable onComplete) {
        limit.setUserId(userId);
        db.upsertAppLimit("resolution=merge-duplicates,return=minimal", limit)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        if (!r.isSuccessful()) Log.e(TAG, "saveAppLimit failed: " + r.code());
                        if (onComplete != null) onComplete.run();
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "saveAppLimit error", t);
                        if (onComplete != null) onComplete.run();
                    }
                });
    }

    /** PATCH — reliably updates an existing limit row without needing INSERT permission. */
    public void updateAppLimit(String userId, String packageName,
                               int newMinutes, Runnable onComplete) {
        Map<String, Object> body = new HashMap<>();
        body.put("daily_limit_minutes", newMinutes);
        body.put("is_active", true);
        db.patchAppLimit("return=minimal", "eq." + userId, "eq." + packageName, body)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        if (!r.isSuccessful()) Log.e(TAG, "updateAppLimit failed: " + r.code());
                        if (onComplete != null) onComplete.run();
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "updateAppLimit error", t);
                        if (onComplete != null) onComplete.run();
                    }
                });
    }

    public void deleteAppLimit(String userId, String packageName) {
        db.deleteAppLimit("eq." + userId, "eq." + packageName)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        if (!r.isSuccessful()) Log.e(TAG, "deleteAppLimit failed: " + r.code());
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "deleteAppLimit error", t);
                    }
                });
    }

    /**
     * Returns the user's limits list, or {@code null} if the network call failed
     * (no connection, server error). An empty list means "Supabase returned no
     * rows" — i.e. the user genuinely has zero limits. The caller MUST treat
     * null and [] differently so the local SQLite cache is not wiped offline.
     */
    public void fetchAppLimits(String userId, Callback1<List<AppLimit>> callback) {
        db.getAppLimits("eq." + userId, "app_name.asc")
                .enqueue(new Callback<List<AppLimit>>() {
                    @Override public void onResponse(Call<List<AppLimit>> call,
                                                     Response<List<AppLimit>> r) {
                        if (r.isSuccessful() && r.body() != null) {
                            callback.onResult(r.body());
                        } else {
                            // HTTP error (auth, 5xx, etc.) — treat as failure
                            Log.w(TAG, "fetchAppLimits HTTP " + r.code());
                            callback.onResult(null);
                        }
                    }
                    @Override public void onFailure(Call<List<AppLimit>> call, Throwable t) {
                        // Network/IO failure — treat as offline
                        Log.e(TAG, "fetchAppLimits error", t);
                        callback.onResult(null);
                    }
                });
    }

    public void fetchUser(String userId, Callback1<User> callback) {
        db.getUser("eq." + userId).enqueue(new Callback<List<User>>() {
            @Override public void onResponse(Call<List<User>> call, Response<List<User>> r) {
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    callback.onResult(r.body().get(0));
                } else {
                    callback.onResult(null);
                }
            }
            @Override public void onFailure(Call<List<User>> call, Throwable t) {
                Log.e(TAG, "fetchUser error", t);
                callback.onResult(null);
            }
        });
    }

    public void fetchGuardianEmail(String userId, Callback1<String> callback) {
        db.getUser("eq." + userId).enqueue(new Callback<List<User>>() {
            @Override public void onResponse(Call<List<User>> call, Response<List<User>> r) {
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    callback.onResult(r.body().get(0).getGuardianEmail());
                } else {
                    callback.onResult(null);
                }
            }
            @Override public void onFailure(Call<List<User>> call, Throwable t) {
                Log.e(TAG, "fetchGuardianEmail error", t);
                callback.onResult(null);
            }
        });
    }
}
