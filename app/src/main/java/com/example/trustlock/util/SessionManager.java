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
    public void setAccessToken(String token)    { prefs.edit().putString(KEY_TOKEN, token).apply(); }
    public void setGuardianEmail(String email)  { prefs.edit().putString(KEY_GUARDIAN, email).apply(); }

    public String getRefreshToken()          { return prefs.getString(KEY_REFRESH_TOKEN, null); }
    public void   setRefreshToken(String v)  { prefs.edit().putString(KEY_REFRESH_TOKEN, v).apply(); }

    public String getUserName()          { return prefs.getString(KEY_USER_NAME, null); }
    public void   setUserName(String v)  { prefs.edit().putString(KEY_USER_NAME, v).apply(); }

    public String getUserEmail()          { return prefs.getString(KEY_USER_EMAIL, null); }
    public void   setUserEmail(String v)  { prefs.edit().putString(KEY_USER_EMAIL, v).apply(); }

    public String getPassword()          { return prefs.getString(KEY_PASSWORD, null); }
    public void   setPassword(String v)  { prefs.edit().putString(KEY_PASSWORD, v).apply(); }

    public void clear() { prefs.edit().clear().apply(); }
}
