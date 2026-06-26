package com.example.trustlock.util;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

/**
 * Holds the {@code user_uid} of the linked user this guardian is currently
 * monitoring. A guardian can be linked to multiple users; in Step 5b the
 * settings tab will let them switch between linked users.
 *
 * For now this is auto-populated with the first guardian_links row on
 * guardian-side cold start; fragments observe {@link #monitoredUserUid} so
 * they refresh when the selection changes.
 */
public class GuardianContext {

    private static final GuardianContext INSTANCE = new GuardianContext();

    /** uid of the user currently being monitored. null until a link is loaded. */
    public final MutableLiveData<String> monitoredUserUid = new MutableLiveData<>(null);

    /** Display name of the monitored user, best-effort. */
    public final MutableLiveData<String> monitoredUserName = new MutableLiveData<>(null);

    private GuardianContext() {}

    public static GuardianContext getInstance() { return INSTANCE; }

    public void setMonitored(@Nullable String uid, @Nullable String name) {
        monitoredUserUid.postValue(uid);
        monitoredUserName.postValue(name);
    }

    @Nullable
    public String getMonitoredUserUidValue() { return monitoredUserUid.getValue(); }
}
