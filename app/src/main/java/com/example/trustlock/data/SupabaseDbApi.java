package com.example.trustlock.data;

import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.models.CurrentApp;
import com.example.trustlock.models.GuardianLink;
import com.example.trustlock.models.UsageLog;
import com.example.trustlock.models.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseDbApi {

    // ── Users ──────────────────────────────────────────────────────────────────

    @POST("rest/v1/users")
    Call<Void> insertUser(@Header("Prefer") String prefer, @Body User user);

    @GET("rest/v1/users")
    Call<List<User>> getUser(@Query("uid") String uidEq);

    /** PATCH a user's row. Use new schema column names: {@code uid=eq.<uuid>}. */
    @PATCH("rest/v1/users")
    Call<Void> patchUser(@Header("Prefer") String prefer,
                         @Query("uid") String uidEq,
                         @Body Map<String, Object> updates);

    /** Lookup a user by their published pairing code (guardian side). */
    @GET("rest/v1/users")
    Call<List<User>> getUserByPairingCode(@Query("pairing_code") String pairingCodeEq);

    // ── Guardian Links ─────────────────────────────────────────────────────────

    @POST("rest/v1/guardian_links")
    Call<List<GuardianLink>> insertGuardianLink(@Header("Prefer") String prefer,
                                                @Body GuardianLink link);

    @GET("rest/v1/guardian_links")
    Call<List<GuardianLink>> getGuardianLinks(@Query("guardian_uid") String guardianUidEq);

    // ── App Limits ─────────────────────────────────────────────────────────────

    @GET("rest/v1/app_limits")
    Call<List<AppLimit>> getAppLimits(@Query("user_id") String userIdEq,
                                      @Query("order") String order);

    @POST("rest/v1/app_limits")
    Call<Void> upsertAppLimit(@Header("Prefer") String prefer, @Body AppLimit limit);

    /** PATCH updates an existing row's fields without needing INSERT permission. */
    @PATCH("rest/v1/app_limits")
    Call<Void> patchAppLimit(@Header("Prefer") String prefer,
                             @Query("user_id") String userIdEq,
                             @Query("package_name") String pkgEq,
                             @Body Map<String, Object> updates);

    @DELETE("rest/v1/app_limits")
    Call<Void> deleteAppLimit(@Query("user_id") String userIdEq,
                              @Query("package_name") String packageNameEq);

    // ── Current App (guardian live view) ──────────────────────────────────────

    /** Returns a singleton list (one row per user) — empty if no foreground report yet. */
    @GET("rest/v1/current_app")
    Call<List<CurrentApp>> getCurrentApp(@Query("user_uid") String userUidEq);

    // ── Usage Logs (guardian dashboard + live monitoring) ─────────────────────

    /** Today's per-app usage rows for a user, sorted by the caller. */
    @GET("rest/v1/usage_logs")
    Call<List<UsageLog>> getUsageLogs(@Query("user_uid") String userUidEq,
                                      @Query("log_date") String logDateEq,
                                      @Query("order") String order);

    /** Weekly aggregate: rows in [gte, lte] for the given user. */
    @GET("rest/v1/usage_logs")
    Call<List<UsageLog>> getUsageLogsBetween(@Query("user_uid") String userUidEq,
                                             @Query("log_date") String gte,
                                             @Query("log_date") String lte,
                                             @Query("order") String order);

    // ── Approval Requests ──────────────────────────────────────────────────────

    @POST("rest/v1/approval_requests")
    Call<List<ApprovalRequest>> insertApprovalRequest(@Header("Prefer") String prefer,
                                                       @Body ApprovalRequest request);

    @GET("rest/v1/approval_requests")
    Call<List<ApprovalRequest>> getApprovalRequest(@Query("id") String idEq);

    @PATCH("rest/v1/approval_requests")
    Call<Void> updateApprovalStatus(@Query("id") String idEq,
                                    @Body Map<String, String> update);
}
