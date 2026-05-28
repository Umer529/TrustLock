package com.example.trustlock.data;

import android.util.Log;

import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.User;

import java.util.ArrayList;
import java.util.List;

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

    public void fetchAppLimits(String userId, Callback1<List<AppLimit>> callback) {
        db.getAppLimits("eq." + userId, "app_name.asc")
                .enqueue(new Callback<List<AppLimit>>() {
                    @Override public void onResponse(Call<List<AppLimit>> call,
                                                     Response<List<AppLimit>> r) {
                        callback.onResult(r.isSuccessful() && r.body() != null
                                ? r.body() : new ArrayList<>());
                    }
                    @Override public void onFailure(Call<List<AppLimit>> call, Throwable t) {
                        Log.e(TAG, "fetchAppLimits error", t);
                        callback.onResult(new ArrayList<>());
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
