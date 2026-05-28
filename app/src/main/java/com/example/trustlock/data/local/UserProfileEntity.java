package com.example.trustlock.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfileEntity {
    @PrimaryKey @NonNull
    public String id = "";
    public String name;
    public String email;
    public String guardianEmail;
}
