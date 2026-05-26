package com.example.trustlock.data;

/**
 * Canonical Firestore collection names and schema documentation.
 *
 * Schema:
 *   /users/{uid}                              → User document
 *   /users/{uid}/appLimits/{packageName}      → AppLimit document
 *   /approvalRequests/{requestId}             → ApprovalRequest document
 */
public final class FirestoreCollections {

    public static final String USERS = "users";
    public static final String APP_LIMITS = "appLimits";
    public static final String APPROVAL_REQUESTS = "approvalRequests";

    private FirestoreCollections() {}
}
