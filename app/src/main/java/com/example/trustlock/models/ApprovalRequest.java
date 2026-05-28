package com.example.trustlock.models;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class ApprovalRequest {

    public static final String TYPE_CHANGE_LIMIT = "CHANGE_LIMIT";
    public static final String TYPE_UNINSTALL    = "UNINSTALL";
    public static final String TYPE_EXTRA_TIME   = "EXTRA_TIME";

    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DENIED   = "DENIED";

    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("type")
    private String type;

    @SerializedName("requested_by")
    private String requestedBy;

    @SerializedName("guardian_email")
    private String guardianEmail;

    @SerializedName("status")
    private String status;

    @SerializedName("payload")
    private Map<String, Object> payload;

    public ApprovalRequest() {}

    public ApprovalRequest(String userId, String type, String requestedBy,
                           String guardianEmail, String status, Map<String, Object> payload) {
        this.userId        = userId;
        this.type          = type;
        this.requestedBy   = requestedBy;
        this.guardianEmail = guardianEmail;
        this.status        = status;
        this.payload       = payload;
    }

    public String getId()               { return id; }
    public void   setId(String v)       { id = v; }

    public String getUserId()           { return userId; }
    public void   setUserId(String v)   { userId = v; }

    public String getType()             { return type; }
    public void   setType(String v)     { type = v; }

    public String getRequestedBy()          { return requestedBy; }
    public void   setRequestedBy(String v)  { requestedBy = v; }

    public String getGuardianEmail()          { return guardianEmail; }
    public void   setGuardianEmail(String v)  { guardianEmail = v; }

    public String getStatus()           { return status; }
    public void   setStatus(String v)   { status = v; }

    public Map<String, Object> getPayload()          { return payload; }
    public void                setPayload(Map<String, Object> v) { payload = v; }
}
