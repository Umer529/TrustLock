package com.example.trustlock.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS_NAME      = "trustlock_session";
    private static final String KEY_USER_ID     = "user_id";
    private static final String KEY_TOKEN       = "access_token";
    private static final String KEY_GUARDIAN    = "guardian_email";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_NAME    = "user_name";
    private static final String KEY_USER_EMAIL   = "user_email";
    private static final String KEY_PASSWORD     = "password";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
    }

    public static SessionManager getInstance() { return instance; }

    public boolean isLoggedIn() { return getUserId() != null; }

    public String getUserId()      { return prefs.getString(KEY_USER_ID, null); }
    public String getAccessToken() { return prefs.getString(KEY_TOKEN, null); }
    public String getGuardianEmail() { return prefs.getString(KEY_GUARDIAN, null); }

    public void setUserId(String id)           { prefs.edit().putString(KEY_USER_ID, id).apply(); }
    public void setAccessToken(String token)    {
        prefs.edit().putString(KEY_TOKEN, token).apply();
        // Mirror into the Realtime client so subscriptions pick up the new JWT
        // (RLS policies depend on auth.uid() resolving from this token).
        try {
            com.example.trustlock.data.RealtimeManager.getInstance().setAccessToken(token);
        } catch (IllegalStateException ignored) {
            // RealtimeManager not initialised yet (very early boot) — fine.
        }
    }
    public void setGuardianEmail(String email)  { prefs.edit().putString(KEY_GUARDIAN, email).apply(); }

    public String getRefreshToken()          { return prefs.getString(KEY_REFRESH_TOKEN, null); }
    public void   setRefreshToken(String v)  { prefs.edit().putString(KEY_REFRESH_TOKEN, v).apply(); }

    public String getUserName()          { return prefs.getString(KEY_USER_NAME, null); }
    public void   setUserName(String v)  { prefs.edit().putString(KEY_USER_NAME, v).apply(); }

    public String getUserEmail()          { return prefs.getString(KEY_USER_EMAIL, null); }
    public void   setUserEmail(String v)  { prefs.edit().putString(KEY_USER_EMAIL, v).apply(); }

    public String getPassword()          { return prefs.getString(KEY_PASSWORD, null); }
    public void   setPassword(String v)  { prefs.edit().putString(KEY_PASSWORD, v).apply(); }

    // ── Pending approval request (background polling) ──────────────────────────
    private static final String KEY_PENDING_ID      = "pending_request_id";
    private static final String KEY_PENDING_TYPE    = "pending_request_type";
    private static final String KEY_PENDING_PKG     = "pending_package_name";

    public String getPendingRequestId()           { return prefs.getString(KEY_PENDING_ID,   null); }
    public String getPendingRequestType()         { return prefs.getString(KEY_PENDING_TYPE, null); }
    public String getPendingPackageName()         { return prefs.getString(KEY_PENDING_PKG,  null); }

    public void setPendingRequest(String id, String type, String packageName) {
        prefs.edit()
                .putString(KEY_PENDING_ID,   id)
                .putString(KEY_PENDING_TYPE, type)
                .putString(KEY_PENDING_PKG,  packageName)
                .apply();
    }

    public void clearPendingRequest() {
        prefs.edit()
                .remove(KEY_PENDING_ID)
                .remove(KEY_PENDING_TYPE)
                .remove(KEY_PENDING_PKG)
                .apply();
    }

    // ── Approved uninstall (set by service, consumed by MainActivity) ──────────
    private static final String KEY_UNINSTALL_APPROVED = "uninstall_approved";

    public boolean isUninstallApproved() {
        return prefs.getBoolean(KEY_UNINSTALL_APPROVED, false);
    }

    public void setUninstallApproved(boolean approved) {
        prefs.edit().putBoolean(KEY_UNINSTALL_APPROVED, approved).apply();
    }

    public void clear() { prefs.edit().clear().apply(); }
}
