package com.example.trustlock.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.trustlock.data.UserRepository;
import com.example.trustlock.models.AppInfo;
import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.util.UsageStatsHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLimitViewModel extends AndroidViewModel {

    private final Context context;
    private final FirebaseAuth auth;
    private final UserRepository userRepository;

    private final LiveData<List<AppLimit>> appLimits;
    private final MutableLiveData<List<AppInfo>> installedApps = new MutableLiveData<>();

    /** True after a new limit is saved directly (no approval needed). */
    private final MutableLiveData<Boolean> limitSaved = new MutableLiveData<>();
    /** Set to the new ApprovalRequest's ID when guardian approval is required. */
    private final MutableLiveData<String> pendingRequestId = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public AppLimitViewModel(@NonNull Application application) {
        super(application);
        this.context = application.getApplicationContext();
        this.auth = FirebaseAuth.getInstance();
        this.userRepository = new UserRepository(FirebaseFirestore.getInstance());

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            appLimits = userRepository.getAppLimits(user.getUid());
        } else {
            appLimits = new MutableLiveData<>(new ArrayList<>());
        }

        loadInstalledApps();
    }

    // ─── Exposed LiveData ────────────────────────────────────────────────────

    public LiveData<List<AppLimit>> getAppLimits() { return appLimits; }
    public LiveData<List<AppInfo>> getInstalledApps() { return installedApps; }
    public LiveData<Boolean> getLimitSaved() { return limitSaved; }
    public LiveData<String> getPendingRequestId() { return pendingRequestId; }
    public LiveData<String> getError() { return error; }

    // ─── Actions ─────────────────────────────────────────────────────────────

    /**
     * Saves a new limit directly, or creates an ApprovalRequest when the app
     * already has a limit (an edit requires guardian sign-off).
     */
    public void requestLimitChange(@NonNull AppLimit newLimit) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            error.setValue("Not signed in");
            return;
        }

        List<AppLimit> current = appLimits.getValue();
        boolean alreadyLimited = current != null && current.stream()
                .anyMatch(l -> l.getPackageName().equals(newLimit.getPackageName()));

        if (alreadyLimited) {
            // Changing an existing limit needs guardian approval
            Map<String, Object> payload = new HashMap<>();
            payload.put("packageName", newLimit.getPackageName());
            payload.put("appName", newLimit.getAppName());
            payload.put("newLimitMinutes", newLimit.getDailyLimitMinutes());

            ApprovalRequest request = new ApprovalRequest();
            request.setUserId(user.getUid());
            request.setType(ApprovalRequest.TYPE_CHANGE_LIMIT);
            request.setRequestedBy(user.getUid());
            request.setStatus(ApprovalRequest.STATUS_PENDING);
            request.setPayload(payload);
            // guardianEmail wired in Step 7 via User document lookup

            userRepository.createApprovalRequest(request);
            pendingRequestId.setValue(request.getId());
        } else {
            // New app — save directly
            newLimit.setActive(true);
            userRepository.saveAppLimit(user.getUid(), newLimit);
            limitSaved.setValue(true);
        }
    }

    /** Returns today's foreground minutes for the given package. Thread-safe (reads OS stats). */
    public long getTodayUsageMinutes(String packageName) {
        return UsageStatsHelper.getTodayUsageMinutes(context, packageName);
    }

    // ─── Package list loading ─────────────────────────────────────────────────

    private void loadInstalledApps() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> all = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppInfo> userApps = new ArrayList<>();
            for (ApplicationInfo info : all) {
                if (!isSystemApp(info)) {
                    String label = pm.getApplicationLabel(info).toString();
                    userApps.add(new AppInfo(info.packageName, label));
                }
            }
            Collections.sort(userApps,
                    (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            installedApps.postValue(userApps);
        });
        executor.shutdown();
    }

    private static boolean isSystemApp(ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }
}
