package com.example.trustlock.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.models.User;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {

    private static final String TAG = "UserRepository";

    private final FirebaseFirestore db;

    public UserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // ─── Write operations ────────────────────────────────────────────────────

    public void saveUser(@NonNull User user) {
        db.collection(FirestoreCollections.USERS)
                .document(user.getUid())
                .set(user)
                .addOnFailureListener(e -> Log.e(TAG, "saveUser failed", e));
    }

    public void saveAppLimit(@NonNull String uid, @NonNull AppLimit limit) {
        db.collection(FirestoreCollections.USERS)
                .document(uid)
                .collection(FirestoreCollections.APP_LIMITS)
                .document(limit.getPackageName())
                .set(limit)
                .addOnFailureListener(e -> Log.e(TAG, "saveAppLimit failed", e));
    }

    /**
     * Writes an ApprovalRequest. Firestore auto-generates the document ID and
     * writes it back into the {@code id} field so guardians can query by ID.
     */
    public void createApprovalRequest(@NonNull ApprovalRequest request) {
        DocumentReference ref = db.collection(FirestoreCollections.APPROVAL_REQUESTS).document();
        request.setId(ref.getId());
        ref.set(request)
                .addOnFailureListener(e -> Log.e(TAG, "createApprovalRequest failed", e));
    }

    // ─── Read operations (real-time LiveData) ────────────────────────────────

    /**
     * Returns a LiveData that emits the latest User snapshot.
     * The Firestore listener is active only while there is at least one observer,
     * and is removed automatically when there are none (avoids leaks).
     */
    public LiveData<User> getUser(@NonNull String uid) {
        DocumentReference ref = db.collection(FirestoreCollections.USERS).document(uid);
        return new DocumentLiveData<>(ref, User.class);
    }

    /**
     * Returns a LiveData that emits the full list of AppLimits for a user
     * every time the subcollection changes.
     */
    public LiveData<List<AppLimit>> getAppLimits(@NonNull String uid) {
        Query query = db.collection(FirestoreCollections.USERS)
                .document(uid)
                .collection(FirestoreCollections.APP_LIMITS);
        return new QueryLiveData<>(query, AppLimit.class);
    }

    /**
     * Returns a LiveData that streams status updates for a single ApprovalRequest.
     * Useful for polling the guardian's APPROVED / DENIED response in real time.
     */
    public LiveData<ApprovalRequest> listenToApprovalRequest(@NonNull String requestId) {
        DocumentReference ref = db.collection(FirestoreCollections.APPROVAL_REQUESTS)
                .document(requestId);
        return new DocumentLiveData<>(ref, ApprovalRequest.class);
    }

    // ─── Lifecycle-aware Firestore LiveData helpers ──────────────────────────

    /**
     * LiveData backed by a single Firestore document.
     * Attaches a real-time listener on {@link #onActive()} and detaches it on
     * {@link #onInactive()}, so it never leaks a listener after the last observer
     * is removed.
     */
    private static final class DocumentLiveData<T> extends LiveData<T> {

        private final DocumentReference ref;
        private final Class<T> type;
        private ListenerRegistration registration;

        DocumentLiveData(DocumentReference ref, Class<T> type) {
            this.ref = ref;
            this.type = type;
        }

        @Override
        protected void onActive() {
            registration = ref.addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    Log.w(TAG, "Document listen failed: " + ref.getPath(), error);
                    return;
                }
                if (snapshot != null && snapshot.exists()) {
                    postValue(snapshot.toObject(type));
                } else {
                    postValue(null);
                }
            });
        }

        @Override
        protected void onInactive() {
            if (registration != null) {
                registration.remove();
                registration = null;
            }
        }
    }

    /**
     * LiveData backed by a Firestore collection query.
     * Follows the same attach/detach lifecycle as {@link DocumentLiveData}.
     */
    private static final class QueryLiveData<T> extends LiveData<List<T>> {

        private final Query query;
        private final Class<T> type;
        private ListenerRegistration registration;

        QueryLiveData(Query query, Class<T> type) {
            this.query = query;
            this.type = type;
        }

        @Override
        protected void onActive() {
            registration = query.addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    Log.w(TAG, "Query listen failed", error);
                    return;
                }
                if (snapshots == null) return;

                List<T> items = new ArrayList<>(snapshots.size());
                for (QueryDocumentSnapshot doc : snapshots) {
                    T item = doc.toObject(type);
                    items.add(item);
                }
                postValue(items);
            });
        }

        @Override
        protected void onInactive() {
            if (registration != null) {
                registration.remove();
                registration = null;
            }
        }
    }
}
