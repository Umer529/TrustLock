package com.example.trustlock.models;

import com.google.gson.annotations.SerializedName;

/** Row in {@code public.guardian_links}. */
public class GuardianLink {

    @SerializedName("id")
    private String id;

    @SerializedName("guardian_uid")
    private String guardianUid;

    @SerializedName("user_uid")
    private String userUid;

    @SerializedName("linked_at")
    private String linkedAt;

    public GuardianLink() {}

    public GuardianLink(String guardianUid, String userUid) {
        this.guardianUid = guardianUid;
        this.userUid     = userUid;
    }

    public String getId()           { return id; }
    public void   setId(String v)   { id = v; }

    public String getGuardianUid()          { return guardianUid; }
    public void   setGuardianUid(String v)  { guardianUid = v; }

    public String getUserUid()          { return userUid; }
    public void   setUserUid(String v)  { userUid = v; }

    public String getLinkedAt()          { return linkedAt; }
    public void   setLinkedAt(String v)  { linkedAt = v; }
}
