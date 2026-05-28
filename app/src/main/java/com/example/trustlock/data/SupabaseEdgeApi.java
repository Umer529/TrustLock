package com.example.trustlock.data;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SupabaseEdgeApi {

    @POST("functions/v1/send-approval-email")
    Call<Void> sendApprovalEmail(@Body SendEmailRequest request);

    class SendEmailRequest {
        @SerializedName("requestId")     public final String  requestId;
        @SerializedName("guardianEmail") public final String  guardianEmail;
        @SerializedName("wardName")      public final String  wardName;
        @SerializedName("description")   public final String  description;
        @SerializedName("isAlert")       public final boolean isAlert;

        /** Standard approval request (Approve / Deny buttons in email). */
        public SendEmailRequest(String requestId, String guardianEmail,
                                String wardName, String description) {
            this.requestId     = requestId;
            this.guardianEmail = guardianEmail;
            this.wardName      = wardName;
            this.description   = description;
            this.isAlert       = false;
        }

        /** Alert-only email (no action buttons — just notifies the guardian). */
        public static SendEmailRequest alert(String guardianEmail,
                                             String wardName, String description) {
            return new SendEmailRequest(null, guardianEmail, wardName, description, true);
        }

        private SendEmailRequest(String requestId, String guardianEmail,
                                 String wardName, String description, boolean isAlert) {
            this.requestId     = requestId;
            this.guardianEmail = guardianEmail;
            this.wardName      = wardName;
            this.description   = description;
            this.isAlert       = isAlert;
        }
    }
}
