package com.example.trustlock.viewmodel;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.trustlock.data.UserRepository;
import com.example.trustlock.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegistrationViewModel extends ViewModel {

    public enum RegistrationState { IDLE, LOADING, SUCCESS, ERROR }

    private final FirebaseAuth auth;
    private final UserRepository userRepository;

    // Per-step field storage (shared across fragment instances)
    private final MutableLiveData<String> name = new MutableLiveData<>("");
    private final MutableLiveData<String> email = new MutableLiveData<>("");
    private final MutableLiveData<String> guardianEmail = new MutableLiveData<>("");
    private String pin = "";

    private final MutableLiveData<RegistrationState> registrationState =
            new MutableLiveData<>(RegistrationState.IDLE);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public RegistrationViewModel() {
        this.auth = FirebaseAuth.getInstance();
        this.userRepository = new UserRepository(FirebaseFirestore.getInstance());
    }

    // ─── Field setters ───────────────────────────────────────────────────────

    public void setName(String value) { name.setValue(value); }
    public void setEmail(String value) { email.setValue(value); }
    public void setGuardianEmail(String value) { guardianEmail.setValue(value); }
    public void setPin(String value) { this.pin = value; }

    // ─── Field getters (for restoring UI state when fragment re-attaches) ────

    public LiveData<String> getName() { return name; }
    public LiveData<String> getEmail() { return email; }
    public LiveData<String> getGuardianEmail() { return guardianEmail; }
    public LiveData<RegistrationState> getRegistrationState() { return registrationState; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    // ─── Validation ──────────────────────────────────────────────────────────

    public boolean isValidEmail(String input) {
        return input != null
                && !input.trim().isEmpty()
                && Patterns.EMAIL_ADDRESS.matcher(input.trim()).matches();
    }

    // ─── Firebase registration ───────────────────────────────────────────────

    /**
     * Signs in anonymously, saves the User document to Firestore, then stores
     * the PIN locally (PIN storage via PinManager is wired in Step 9).
     */
    public void saveUserToFirebase() {
        registrationState.setValue(RegistrationState.LOADING);

        auth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();

                    User user = new User(
                            uid,
                            name.getValue(),
                            email.getValue(),
                            guardianEmail.getValue(),
                            null,   // FCM token — updated by FCM service on first launch
                            null    // createdAt — @ServerTimestamp fills this in
                    );

                    userRepository.saveUser(user);
                    registrationState.setValue(RegistrationState.SUCCESS);
                })
                .addOnFailureListener(e -> {
                    errorMessage.setValue("Registration failed: " + e.getMessage());
                    registrationState.setValue(RegistrationState.ERROR);
                });
    }
}
