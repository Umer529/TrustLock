package com.example.trustlock.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserProfileEntity profile);

    @Query("SELECT * FROM user_profile WHERE id = :userId LIMIT 1")
    UserProfileEntity getById(String userId);
}
