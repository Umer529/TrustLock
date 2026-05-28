package com.example.trustlock.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {UserProfileEntity.class, AppLimitEntity.class}, version = 1, exportSchema = false)
public abstract class LocalDatabase extends RoomDatabase {

    private static volatile LocalDatabase instance;

    public abstract UserProfileDao userProfileDao();
    public abstract AppLimitDao    appLimitDao();

    public static LocalDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (LocalDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            LocalDatabase.class,
                            "trustlock.db"
                    ).build();
                }
            }
        }
        return instance;
    }
}
