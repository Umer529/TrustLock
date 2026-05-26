package com.example.trustlock.models;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.Map;

public class ApprovalRequest {

    /** Common values: "CHANGE_LIMIT", "UNINSTALL" */
    public static final String TYPE_CHANGE_LIMIT = "CHANGE_LIMIT";
    public static final String TYPE_UNINSTALL = "UNINSTALL";

    /** Common values: "PENDING", "APPROVED", "DENIED" */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DENIED = "DENIED";

    private String id;
    private String userId;
    private String type;
    private String requestedBy;
    private String guardianEmail;
    private String status;

    /** Flexible payload — stores the proposed change details (e.g. new limit values). */
    private Map<String, Object> payload;

    @ServerTimestamp
    private Date createdAt;

    /** Required by Firestore for deserialization. */
    public ApprovalRequest() {}

    public ApprovalRequest(String id, String userId, String type, String requestedBy,
                           String guardianEmail, String status,
                           Map<String, Object> payload, Date createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.requestedBy = requestedBy;
        this.guardianEmail = guardianEmail;
        this.status = status;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getGuardianEmail() { return guardianEmail; }
    public void setGuardianEmail(String guardianEmail) { this.guardianEmail = guardianEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
