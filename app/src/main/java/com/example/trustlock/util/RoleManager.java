package com.example.trustlock.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.trustlock.models.Role;

/**
 * Stores the user's chosen Role in an EncryptedSharedPreferences file.
 *
 * Encryption keeps the role tamper-resistant against on-device meddling — a
 * USER can't flip themselves to GUARDIAN by editing prefs XML on a rooted phone.
 * The source of truth lives in Supabase ({@code users.role}); this class is
 * only the local cache that gates which navigation graph MainActivity loads
 * at startup, before the network has had a chance to answer.
 */
public class RoleManager {

    private static final String TAG       = "RoleManager";
    private static final String PREFS     = "screenpact_role";
    private static final String KEY_ROLE  = "user_role";

    private static volatile RoleManager instance;

    private final SharedPreferences prefs;

    private RoleManager(Context appContext) {
        SharedPreferences sp;
        try {
            MasterKey key = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sp = EncryptedSharedPreferences.create(
                    appContext,
                    PREFS,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Throwable t) {
            // Keystore problems are rare but happen on some custom ROMs.
            // Fall back to a regular SharedPreferences so the app still runs;
            // we log loudly so we notice during testing.
            Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back to plain", t);
            sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
        this.prefs = sp;
    }

    public static synchronized void init(Context appContext) {
        if (instance == null) {
            instance = new RoleManager(appContext.getApplicationContext());
        }
    }

    public static RoleManager getInstance() {
        if (instance == null) throw new IllegalStateException("Call RoleManager.init() first");
        return instance;
    }

    @Nullable
    public Role getRole() {
        return Role.fromString(prefs.getString(KEY_ROLE, null));
    }

    public void setRole(@Nullable Role role) {
        prefs.edit().putString(KEY_ROLE, role == null ? null : role.name()).apply();
    }

    public boolean hasRole() {
        return getRole() != null;
    }

    public void clear() {
        prefs.edit().remove(KEY_ROLE).apply();
    }
}
