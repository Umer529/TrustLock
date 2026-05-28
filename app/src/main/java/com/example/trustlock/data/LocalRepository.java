package com.example.trustlock.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.trustlock.data.local.AppLimitDao;
import com.example.trustlock.data.local.AppLimitEntity;
import com.example.trustlock.data.local.LocalDatabase;
import com.example.trustlock.data.local.UserProfileDao;
import com.example.trustlock.data.local.UserProfileEntity;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LocalRepository {

    public interface Callback<T> { void onResult(T result); }

    private final UserProfileDao profileDao;
    private final AppLimitDao    limitDao;
    private final Executor       executor = Executors.newSingleThreadExecutor();

    public LocalRepository(Context context) {
        LocalDatabase db = LocalDatabase.getInstance(context);
        profileDao = db.userProfileDao();
        limitDao   = db.appLimitDao();
    }

    public void saveProfile(UserProfileEntity profile) {
        executor.execute(() -> profileDao.insert(profile));
    }

    public void getProfile(String userId, Callback<UserProfileEntity> callback) {
        executor.execute(() -> {
            UserProfileEntity p = profileDao.getById(userId);
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(p));
        });
    }

    public void saveLimits(List<AppLimitEntity> limits) {
        executor.execute(() -> limitDao.insertAll(limits));
    }

    public void saveLimit(AppLimitEntity limit) {
        executor.execute(() -> limitDao.insert(limit));
    }

    public void deleteLimit(String userId, String packageName) {
        executor.execute(() -> limitDao.delete(userId, packageName));
    }

    public void getLimits(String userId, Callback<List<AppLimitEntity>> callback) {
        executor.execute(() -> {
            List<AppLimitEntity> limits = limitDao.getForUser(userId);
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(limits));
        });
    }
}
