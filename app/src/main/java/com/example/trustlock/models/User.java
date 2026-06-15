package com.example.trustlock.models;

import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("uid")
    private String uid;

    @SerializedName("name")
    private String name;

    @SerializedName("email")
    private String email;

    /** Deprecated: replaced by the uid-based guardian link in v2. Kept for back-compat. */
    @SerializedName("guardian_email")
    private String guardianEmail;

    @SerializedName("fcm_token")
    private String fcmToken;

    @SerializedName("role")
    private String role;

    @SerializedName("phone")
    private String phone;

    @SerializedName("pairing_code")
    private String pairingCode;

    @SerializedName("guardian_uid")
    private String guardianUid;

    public User() {}

    public User(String uid, String name, String email, String guardianEmail, String fcmToken) {
        this.uid           = uid;
        this.name          = name;
        this.email         = email;
        this.guardianEmail = guardianEmail;
        this.fcmToken      = fcmToken;
    }

    public String getUid()          { return uid; }
    public void   setUid(String v)  { uid = v; }

    public String getName()          { return name; }
    public void   setName(String v)  { name = v; }

    public String getEmail()          { return email; }
    public void   setEmail(String v)  { email = v; }

    public String getGuardianEmail()          { return guardianEmail; }
    public void   setGuardianEmail(String v)  { guardianEmail = v; }

    public String getFcmToken()          { return fcmToken; }
    public void   setFcmToken(String v)  { fcmToken = v; }

    public String getRole()          { return role; }
    public void   setRole(String v)  { role = v; }

    public String getPhone()          { return phone; }
    public void   setPhone(String v)  { phone = v; }

    public String getPairingCode()          { return pairingCode; }
    public void   setPairingCode(String v)  { pairingCode = v; }

    public String getGuardianUid()          { return guardianUid; }
    public void   setGuardianUid(String v)  { guardianUid = v; }
}
