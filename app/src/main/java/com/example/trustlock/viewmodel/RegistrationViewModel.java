package com.example.trustlock.viewmodel;

import android.app.Application;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.trustlock.data.LocalRepository;
import com.example.trustlock.data.SupabaseAuthApi;
import com.example.trustlock.data.SupabaseClient;
import com.example.trustlock.data.UserRepository;
import com.example.trustlock.data.local.UserProfileEntity;
import com.example.trustlock.models.User;
import com.example.trustlock.util.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistrationViewModel extends AndroidViewModel {

    public enum RegistrationState { IDLE, LOADING, SUCCESS, ERROR }

    private final MutableLiveData<String> name          = new MutableLiveData<>("");
    private final MutableLiveData<String> email         = new MutableLiveData<>("");
    private final MutableLiveData<String> password      = new MutableLiveData<>("");
    private final MutableLiveData<String> guardianEmail = new MutableLiveData<>("");

    private final MutableLiveData<RegistrationState> registrationState =
            new MutableLiveData<>(RegistrationState.IDLE);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public RegistrationViewModel(@NonNull Application application) {
        super(application);
    }

    public void setName(String v)          { name.setValue(v); }
    public void setEmail(String v)         { email.setValue(v); }
    public void setPassword(String v)      { password.setValue(v); }
    public void setGuardianEmail(String v) { guardianEmail.setValue(v); }

    public LiveData<String>            getName()              { return name; }
    public LiveData<String>            getEmail()             { return email; }
    public LiveData<String>            getPassword()          { return password; }
    public LiveData<String>            getGuardianEmail()     { return guardianEmail; }
    public LiveData<RegistrationState> getRegistrationState() { return registrationState; }
    public LiveData<String>            getErrorMessage()      { return errorMessage; }

    public boolean isValidEmail(String input) {
        return input != null
                && !input.trim().isEmpty()
                && Patterns.EMAIL_ADDRESS.matcher(input.trim()).matches();
    }

    public void saveUserToFirebase() {
        registrationState.setValue(RegistrationState.LOADING);

        String userEmail  = email.getValue();
        String userPass   = password.getValue();

        SupabaseClient.getInstance().auth()
                .signUp(new SupabaseAuthApi.SignUpRequest(userEmail, userPass))
                .enqueue(new Callback<SupabaseAuthApi.AuthResponse>() {
                    @Override
                    public void onResponse(Call<SupabaseAuthApi.AuthResponse> call,
                                           Response<SupabaseAuthApi.AuthResponse> response) {
                        if (!response.isSuccessful()
                                || response.body() == null
                                || response.body().user == null) {
                            errorMessage.postValue("Registration failed. Try a different email.");
                            registrationState.postValue(RegistrationState.ERROR);
                            return;
                        }

                        SupabaseAuthApi.AuthResponse auth = response.body();
                        String uid      = auth.user.id;
                        String userName = name.getValue();
                        String guardian = guardianEmail.getValue();

                        // Persist to session (SharedPreferences)
                        SessionManager session = SessionManager.getInstance();
                        session.setUserId(uid);
                        session.setAccessToken(auth.accessToken);
                        session.setRefreshToken(auth.refreshToken);
                        session.setGuardianEmail(guardian);
                        session.setUserName(userName);
                        session.setUserEmail(userEmail);
                        session.setPassword(userPass);

                        // Persist to Supabase
                        User user = new User(uid, userName, userEmail, guardian, null);
                        new UserRepository().saveUser(user);

                        // Persist locally to SQLite so data is available offline
                        UserProfileEntity profile = new UserProfileEntity();
                        profile.id           = uid;
                        profile.name         = userName;
                        profile.email        = userEmail;
                        profile.guardianEmail = guardian;
                        new LocalRepository(getApplication()).saveProfile(profile);

                        registrationState.postValue(RegistrationState.SUCCESS);
                    }

                    @Override
                    public void onFailure(Call<SupabaseAuthApi.AuthResponse> call, Throwable t) {
                        errorMessage.postValue("Network error: " + t.getMessage());
                        registrationState.postValue(RegistrationState.ERROR);
                    }
                });
    }
}
