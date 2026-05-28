package com.example.trustlock.models;

import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("id")
    private String uid;

    @SerializedName("name")
    private String name;

    @SerializedName("email")
    private String email;

    @SerializedName("guardian_email")
    private String guardianEmail;

    @SerializedName("fcm_token")
    private String fcmToken;

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
}
