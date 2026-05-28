package com.example.trustlock.data;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SupabaseEdgeApi {

    @POST("functions/v1/send-approval-email")
    Call<Void> sendApprovalEmail(@Body SendEmailRequest request);

    class SendEmailRequest {
        @SerializedName("requestId")     public final String requestId;
        @SerializedName("guardianEmail") public final String guardianEmail;
        @SerializedName("wardName")      public final String wardName;
        @SerializedName("description")   public final String description;

        public SendEmailRequest(String requestId, String guardianEmail,
                                String wardName, String description) {
            this.requestId     = requestId;
            this.guardianEmail = guardianEmail;
            this.wardName      = wardName;
            this.description   = description;
        }
    }
}
