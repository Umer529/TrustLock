package com.example.trustlock.data;

import com.example.trustlock.models.AppLimit;
import com.example.trustlock.models.ApprovalRequest;
import com.example.trustlock.models.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
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
    Call<List<User>> getUser(@Query("id") String idEq);

    // ── App Limits ─────────────────────────────────────────────────────────────

    @GET("rest/v1/app_limits")
    Call<List<AppLimit>> getAppLimits(@Query("user_id") String userIdEq,
                                      @Query("order") String order);

    @POST("rest/v1/app_limits")
    Call<Void> upsertAppLimit(@Header("Prefer") String prefer, @Body AppLimit limit);

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
