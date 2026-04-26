package com.bpflow.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "fraud_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAlert {

    @Id
    private String id;

    private String instanceId;
    private String workflowId;
    private String taskId;
    private String userId;

    private AlertType alertType;
    private String description;

    private double riskScore; // 0.0 - 1.0

    @Builder.Default
    private AlertSeverity severity = AlertSeverity.MEDIUM;

    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @Builder.Default
    private List<String> indicators = new ArrayList<>(); // list of anomaly reasons

    // For anomalous timing
    private Long actualDurationMs;
    private Long expectedDurationMs;

    // Reviewer
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;

    @CreatedDate
    private LocalDateTime detectedAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum AlertType {
        ANOMALOUS_TIMING,
        SUSPICIOUS_USER,
        REPETITIVE_APPROVAL,
        FLOW_SKIP,
        UNUSUAL_REASSIGNMENT,
        OFF_HOURS_ACTIVITY,
        HIGH_REJECTION_RATE,
        BULK_APPROVAL
    }

    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertStatus {
        OPEN, UNDER_REVIEW, RESOLVED, FALSE_POSITIVE
    }
}
