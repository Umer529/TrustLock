package com.example.trustlock.data;

import android.util.Log;

import com.example.trustlock.util.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupabaseClient {

    public static final String PROJECT_URL = "https://ytccxodgeigotirihpgo.supabase.co/";
    public static final String ANON_KEY    = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl0Y2N4b2RnZWlnb3RpcmlocGdvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MTUzNzAsImV4cCI6MjA5NTM5MTM3MH0.jg87ryOIkmGrfTDY3lUPoZXnOCFmwdv_1k_ivmjcJtQ";

    private static final String TAG = "SupabaseClient";

    private static volatile SupabaseClient instance;

    private final SupabaseAuthApi authApi;
    private final SupabaseDbApi   dbApi;
    private final SupabaseDbApi   anonDbApi;
    private final SupabaseEdgeApi edgeApi;

    private SupabaseClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Anon client — sends apikey + Authorization(anon) so it works for both
        // PostgREST (apikey) and Edge Functions (Authorization: Bearer)
        OkHttpClient anonClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("apikey", ANON_KEY)
                                .header("Authorization", "Bearer " + ANON_KEY)
                                .header("Content-Type", "application/json")
                                .build()))
                .addInterceptor(logging)
                .build();

        // Authenticated client — adds Bearer token, auto-refreshes on 401
        OkHttpClient authClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    SessionManager sm = SessionManager.getInstance();
                    String token = sm != null ? sm.getAccessToken() : null;
                    Request.Builder rb = chain.request().newBuilder()
                            .header("apikey", ANON_KEY)
                            .header("Content-Type", "application/json");
                    if (token != null) rb.header("Authorization", "Bearer " + token);
                    return chain.proceed(rb.build());
                })
                .authenticator(new okhttp3.Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        // Avoid infinite retry loops
                        if (response.request().header("X-Token-Refreshed") != null) return null;

                        SessionManager sm = SessionManager.getInstance();
                        if (sm == null) return null;

                        String refreshToken = sm.getRefreshToken();
                        if (refreshToken == null) return null;

                        // Synchronous token refresh call (runs on OkHttp's thread pool)
                        String body = "{\"refresh_token\":\"" + refreshToken + "\"}";
                        okhttp3.Request refreshRequest = new okhttp3.Request.Builder()
                                .url(PROJECT_URL + "auth/v1/token?grant_type=refresh_token")
                                .post(RequestBody.create(body.getBytes(),
                                        MediaType.parse("application/json")))
                                .header("apikey", ANON_KEY)
                                .header("Content-Type", "application/json")
                                .build();

                        try (Response refreshResponse = new OkHttpClient().newCall(refreshRequest).execute()) {
                            if (!refreshResponse.isSuccessful() || refreshResponse.body() == null) {
                                Log.e(TAG, "Token refresh failed: " + refreshResponse.code());
                                return null;
                            }

                            String json = refreshResponse.body().string();
                            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                            String newAccess  = obj.has("access_token")  ? obj.get("access_token").getAsString()  : null;
                            String newRefresh = obj.has("refresh_token") ? obj.get("refresh_token").getAsString() : null;

                            if (newAccess == null) return null;

                            sm.setAccessToken(newAccess);
                            if (newRefresh != null) sm.setRefreshToken(newRefresh);

                            Log.d(TAG, "Token refreshed successfully");

                            // Retry original request with new token, mark it to prevent infinite loop
                            return response.request().newBuilder()
                                    .header("Authorization", "Bearer " + newAccess)
                                    .header("X-Token-Refreshed", "true")
                                    .build();
                        }
                    }
                })
                .addInterceptor(logging)
                .build();

        authApi = new Retrofit.Builder()
                .baseUrl(PROJECT_URL)
                .client(anonClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseAuthApi.class);

        dbApi = new Retrofit.Builder()
                .baseUrl(PROJECT_URL)
                .client(authClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseDbApi.class);

        anonDbApi = new Retrofit.Builder()
                .baseUrl(PROJECT_URL)
                .client(anonClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseDbApi.class);

        edgeApi = new Retrofit.Builder()
                .baseUrl(PROJECT_URL)
                .client(anonClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseEdgeApi.class);
    }

    public static SupabaseClient getInstance() {
        if (instance == null) {
            synchronized (SupabaseClient.class) {
                if (instance == null) instance = new SupabaseClient();
            }
        }
        return instance;
    }

    public SupabaseDbApi   db()     { return dbApi; }
    public SupabaseDbApi   anonDb() { return anonDbApi; }
    public SupabaseAuthApi auth()   { return authApi; }
    public SupabaseEdgeApi edge()   { return edgeApi; }
}
