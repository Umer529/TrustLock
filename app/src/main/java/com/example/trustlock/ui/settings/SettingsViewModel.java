package com.example.trustlock.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.trustlock.data.LocalRepository;
import com.example.trustlock.data.UserRepository;
import com.example.trustlock.data.local.UserProfileEntity;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.models.User;
import com.example.trustlock.util.ApprovalRequestManager;
import com.example.trustlock.util.SessionManager;

import java.util.HashMap;
import java.util.Map;

public class SettingsViewModel extends AndroidViewModel {

    public static class PendingApprovalData {
        public final String requestId;
        public final String guardianEmail;
        public final String description;

        PendingApprovalData(String r, String g, String d) {
            requestId = r; guardianEmail = g; description = d;
        }
    }

    private final UserRepository  userRepository;
    private final LocalRepository localRepository;

    private final MutableLiveData<User>                user            = new MutableLiveData<>();
    private final MutableLiveData<PendingApprovalData> pendingApproval = new MutableLiveData<>();
    private final MutableLiveData<String>              error           = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application app) {
        super(app);
        userRepository  = new UserRepository();
        localRepository = new LocalRepository(app);
    }

    public LiveData<User>                getUser()            { return user; }
    public LiveData<PendingApprovalData> getPendingApproval() { return pendingApproval; }
    public LiveData<String>              getError()           { return error; }

    /**
     * Loads user profile: SQLite cache first (instant, works offline),
     * then refreshes from Supabase and updates the cache.
     */
    public void loadProfile() {
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) return;

        // 1. Show local data immediately
        localRepository.getProfile(userId, cached -> {
            if (cached != null) {
                user.postValue(entityToUser(cached));
                // Also refresh session so name/email are available without DB next time
                SessionManager s = SessionManager.getInstance();
                if (s.getUserName() == null) s.setUserName(cached.name);
                if (s.getUserEmail() == null) s.setUserEmail(cached.email);
                if (s.getGuardianEmail() == null) s.setGuardianEmail(cached.guardianEmail);
            }

            // 2. Refresh from network regardless (may have changed on another device)
            userRepository.fetchUser(userId, remote -> {
                if (remote != null) {
                    user.postValue(remote);
                    // Update SQLite cache
                    UserProfileEntity updated = new UserProfileEntity();
                    updated.id            = userId;
                    updated.name          = remote.getName();
                    updated.email         = remote.getEmail();
                    updated.guardianEmail = remote.getGuardianEmail();
                    localRepository.saveProfile(updated);
                    // Update session cache
                    SessionManager s = SessionManager.getInstance();
                    s.setUserName(remote.getName());
                    s.setUserEmail(remote.getEmail());
                    s.setGuardianEmail(remote.getGuardianEmail());
                }
            });
        });
    }

    public void requestUninstall() {
        String userId        = SessionManager.getInstance().getUserId();
        String guardianEmail = SessionManager.getInstance().getGuardianEmail();
        if (userId == null) { error.setValue("Not signed in"); return; }
        if (guardianEmail == null || guardianEmail.isEmpty()) {
            error.setValue("Guardian email not set. Cannot send approval request.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "User requested app uninstall");

        String description = "Remove ScreenPact from this device";
        new ApprovalRequestManager().createApprovalRequest(
                userId, ApprovalRequest.TYPE_UNINSTALL,
                guardianEmail, payload, description,
                requestId -> {
                    if (requestId != null) {
                        // Persist so the background service can poll even if the dialog is dismissed
                        SessionManager.getInstance()
                                .setPendingRequest(requestId, ApprovalRequest.TYPE_UNINSTALL, null);
                        pendingApproval.postValue(
                                new PendingApprovalData(requestId, guardianEmail, description));
                    } else {
                        error.postValue("Failed to send approval request");
                    }
                });
    }

    public void clearPendingApproval() { pendingApproval.setValue(null); }

    private static User entityToUser(UserProfileEntity e) {
        return new User(e.id, e.name, e.email, e.guardianEmail, null);
    }
}
