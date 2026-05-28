package com.example.trustlock.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.trustlock.data.LocalRepository;
import com.example.trustlock.data.UserRepository;
import com.example.trustlock.data.local.AppLimitEntity;
import com.example.trustlock.models.AppInfo;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.util.ApprovalRequestManager;
import com.example.trustlock.util.SessionManager;
import com.example.trustlock.util.UsageStatsHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLimitViewModel extends AndroidViewModel {

    public static class PendingApprovalData {
        public final String requestId;
        public final String guardianEmail;
        public final String description;
        PendingApprovalData(String r, String g, String d) {
            requestId = r; guardianEmail = g; description = d;
        }
    }

    private final Context         context;
    private final UserRepository  userRepository;
    private final LocalRepository localRepository;

    private final MutableLiveData<List<AppLimit>>      appLimits       = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<AppInfo>>       installedApps   = new MutableLiveData<>();
    private final MutableLiveData<Boolean>             limitSaved      = new MutableLiveData<>();
    private final MutableLiveData<PendingApprovalData> pendingApproval = new MutableLiveData<>();
    private final MutableLiveData<String>              error           = new MutableLiveData<>();

    private AppLimit pendingNewLimit;
    private String   cachedGuardianEmail;

    public AppLimitViewModel(@NonNull Application application) {
        super(application);
        this.context         = application.getApplicationContext();
        this.userRepository  = new UserRepository();
        this.localRepository = new LocalRepository(application);
        loadAppLimits();
        loadInstalledApps();
    }

    public LiveData<List<AppLimit>>      getAppLimits()       { return appLimits; }
    public LiveData<List<AppInfo>>       getInstalledApps()   { return installedApps; }
    public LiveData<Boolean>             getLimitSaved()      { return limitSaved; }
    public LiveData<PendingApprovalData> getPendingApproval() { return pendingApproval; }
    public LiveData<String>              getError()           { return error; }

    // ─── Load ────────────────────────────────────────────────────────────────

    public void loadAppLimits() {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;

        localRepository.getLimits(userId, cached -> {
            if (cached != null && !cached.isEmpty())
                appLimits.postValue(entitiesToModels(cached));
        });

        userRepository.fetchAppLimits(userId, remote -> {
            if (remote != null) {
                appLimits.postValue(remote);
                if (!remote.isEmpty())
                    localRepository.saveLimits(modelsToEntities(remote, userId));
            }
        });
    }

    // ─── Set / change limit ───────────────────────────────────────────────────

    public void requestLimitChange(@NonNull AppLimit newLimit) {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) { error.setValue("Not signed in"); return; }

        AppLimit existing = findExistingLimit(newLimit.getPackageName());

        if (existing != null) {
            int    oldMins = existing.getDailyLimitMinutes();
            int    newMins = newLimit.getDailyLimitMinutes();
            String desc    = "Change " + newLimit.getAppName() + " limit from "
                    + formatMinutes(oldMins) + " to " + formatMinutes(newMins);

            Map<String, Object> payload = new HashMap<>();
            payload.put("packageName",         newLimit.getPackageName());
            payload.put("appName",             newLimit.getAppName());
            payload.put("newLimitMinutes",     newMins);
            payload.put("currentLimitMinutes", oldMins);

            pendingNewLimit = newLimit;
            pendingNewLimit.setActive(true);

            fetchGuardianEmailThen(userId, guardianEmail -> {
                new ApprovalRequestManager().createApprovalRequest(
                        userId, ApprovalRequest.TYPE_CHANGE_LIMIT,
                        guardianEmail, payload, desc, requestId -> {
                            if (requestId != null) {
                                // Store so background service can poll if dialog is dismissed
                                SessionManager.getInstance().setPendingRequest(
                                        requestId, ApprovalRequest.TYPE_CHANGE_LIMIT,
                                        newLimit.getPackageName());
                                pendingApproval.postValue(
                                        new PendingApprovalData(requestId, guardianEmail, desc));
                            } else {
                                error.postValue("Failed to send approval request");
                            }
                        });
            });
        } else {
            newLimit.setActive(true);
            // New limit — no guardian approval needed, save immediately
            String uid = userId;
            userRepository.saveAppLimit(uid, newLimit, () -> {
                localRepository.saveLimit(modelToEntity(newLimit, uid));
                limitSaved.postValue(true);
                loadAppLimits();
            });
        }
    }

    /** Called when the WaitingForApprovalDialog reports APPROVED. */
    public void applyPendingLimit() {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null || pendingNewLimit == null) return;

        AppLimit limitToSave = pendingNewLimit;
        pendingNewLimit = null;
        SessionManager.getInstance().clearPendingRequest();

        // Save to Supabase first; only then refresh the local cache + LiveData
        userRepository.saveAppLimit(userId, limitToSave, () -> {
            localRepository.saveLimit(modelToEntity(limitToSave, userId));
            limitSaved.postValue(true);
            loadAppLimits();
        });
    }

    // ─── Delete limit ─────────────────────────────────────────────────────────

    public void deleteLimit(String packageName) {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;

        userRepository.deleteAppLimit(userId, packageName);
        localRepository.deleteLimit(userId, packageName);

        // Remove from the in-memory list immediately so the UI updates without waiting
        List<AppLimit> current = appLimits.getValue();
        if (current != null) {
            List<AppLimit> updated = new ArrayList<>();
            for (AppLimit l : current) {
                if (!packageName.equals(l.getPackageName())) updated.add(l);
            }
            appLimits.postValue(updated);
        }
    }

    // ─── Misc ─────────────────────────────────────────────────────────────────

    public void clearPendingApproval() { pendingApproval.setValue(null); }

    public long getTodayUsageMinutes(String packageName) {
        return UsageStatsHelper.getTodayUsageMinutes(context, packageName);
    }

    private AppLimit findExistingLimit(String packageName) {
        List<AppLimit> current = appLimits.getValue();
        if (current == null) return null;
        for (AppLimit l : current) {
            if (packageName.equals(l.getPackageName())) return l;
        }
        return null;
    }

    private void fetchGuardianEmailThen(String userId,
                                        UserRepository.Callback1<String> callback) {
        if (cachedGuardianEmail != null) { callback.onResult(cachedGuardianEmail); return; }
        String fromSession = SessionManager.getInstance().getGuardianEmail();
        if (fromSession != null && !fromSession.isEmpty()) {
            cachedGuardianEmail = fromSession;
            callback.onResult(cachedGuardianEmail);
            return;
        }
        userRepository.fetchGuardianEmail(userId, email -> {
            cachedGuardianEmail = email != null ? email : "";
            callback.onResult(cachedGuardianEmail);
        });
    }

    private static String formatMinutes(int minutes) {
        if (minutes >= 60) return (minutes / 60) + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }

    // ─── Installed apps loading ───────────────────────────────────────────────

    private void loadInstalledApps() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            android.content.Intent launchIntent =
                    new android.content.Intent(android.content.Intent.ACTION_MAIN);
            launchIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);

            List<android.content.pm.PackageInfo> packages = pm.getInstalledPackages(0);
            List<AppInfo> userApps = new ArrayList<>();
            for (android.content.pm.PackageInfo pkg : packages) {
                String pkgName = pkg.packageName;
                if (pkgName.equals(context.getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(pkgName) != null) {
                    userApps.add(new AppInfo(pkgName,
                            pm.getApplicationLabel(pkg.applicationInfo).toString()));
                }
            }
            Collections.sort(userApps,
                    (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            installedApps.postValue(userApps);
        });
        executor.shutdown();
    }

    // ─── Entity / model conversions ───────────────────────────────────────────

    private static AppLimitEntity modelToEntity(AppLimit m, String userId) {
        AppLimitEntity e = new AppLimitEntity();
        e.userId            = userId;
        e.packageName       = m.getPackageName() != null ? m.getPackageName() : "";
        e.appName           = m.getAppName();
        e.dailyLimitMinutes = m.getDailyLimitMinutes();
        e.active            = m.isActive();
        return e;
    }

    private static AppLimit entityToModel(AppLimitEntity e) {
        AppLimit m = new AppLimit();
        m.setPackageName(e.packageName);
        m.setAppName(e.appName);
        m.setDailyLimitMinutes(e.dailyLimitMinutes);
        m.setActive(e.active);
        m.setUserId(e.userId);
        return m;
    }

    private static List<AppLimitEntity> modelsToEntities(List<AppLimit> models, String userId) {
        List<AppLimitEntity> list = new ArrayList<>();
        for (AppLimit m : models) list.add(modelToEntity(m, userId));
        return list;
    }

    private static List<AppLimit> entitiesToModels(List<AppLimitEntity> entities) {
        List<AppLimit> list = new ArrayList<>();
        for (AppLimitEntity e : entities) list.add(entityToModel(e));
        return list;
    }
}
