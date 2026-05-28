package com.example.trustlock.data;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseAuthApi {

    @POST("auth/v1/signup")
    Call<AuthResponse> signUp(@Body SignUpRequest request);

    @POST("auth/v1/token")
    Call<AuthResponse> refreshToken(@Query("grant_type") String grantType,
                                    @Body RefreshRequest body);

    @POST("auth/v1/token")
    Call<AuthResponse> signIn(@Query("grant_type") String grantType,
                              @Body SignInRequest body);

    class SignUpRequest {
        @SerializedName("email")    public final String email;
        @SerializedName("password") public final String password;

        public SignUpRequest(String email, String password) {
            this.email    = email;
            this.password = password;
        }
    }

    class RefreshRequest {
        @SerializedName("refresh_token") public final String refreshToken;
        public RefreshRequest(String rt) { this.refreshToken = rt; }
    }

    class SignInRequest {
        @SerializedName("email")    public final String email;
        @SerializedName("password") public final String password;
        public SignInRequest(String email, String password) {
            this.email = email; this.password = password;
        }
    }

    class AuthResponse {
        @SerializedName("access_token")  public String accessToken;
        @SerializedName("refresh_token") public String refreshToken;
        @SerializedName("user")          public AuthUser user;

        public static class AuthUser {
            @SerializedName("id") public String id;
        }
    }
}
