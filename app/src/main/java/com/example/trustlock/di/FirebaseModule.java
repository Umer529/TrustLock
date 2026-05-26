package com.example.trustlock.di;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Simple static providers for Firebase instances. Hilt is disabled in this build so
 * keep these as plain static methods; callers can obtain instances directly if
 * needed.
 */
public class FirebaseModule {

    public static FirebaseAuth provideFirebaseAuth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFirestore provideFirebaseFirestore() {
        return FirebaseFirestore.getInstance();
    }

    public static FirebaseMessaging provideFirebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }
}
