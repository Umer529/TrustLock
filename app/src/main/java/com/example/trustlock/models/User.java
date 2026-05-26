package com.example.trustlock.models;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class User {

    private String uid;
    private String name;
    private String email;
    private String guardianEmail;
    private String fcmToken;

    // Firestore fills this in automatically on the first write.
    @ServerTimestamp
    private Date createdAt;

    /** Required by Firestore for deserialization. */
    public User() {}

    public User(String uid, String name, String email, String guardianEmail,
                String fcmToken, Date createdAt) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.guardianEmail = guardianEmail;
        this.fcmToken = fcmToken;
        this.createdAt = createdAt;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getGuardianEmail() { return guardianEmail; }
    public void setGuardianEmail(String guardianEmail) { this.guardianEmail = guardianEmail; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
