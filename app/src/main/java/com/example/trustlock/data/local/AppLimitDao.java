package com.example.trustlock.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppLimitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppLimitEntity> limits);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AppLimitEntity limit);

    @Query("SELECT * FROM app_limits WHERE userId = :userId ORDER BY appName ASC")
    List<AppLimitEntity> getForUser(String userId);

    @Query("DELETE FROM app_limits WHERE userId = :userId AND packageName = :packageName")
    void delete(String userId, String packageName);
}
