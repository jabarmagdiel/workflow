package com.bpflow.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Document(collection = "workflow_instances")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowInstance {

    @Id
    private String id;

    @Indexed
    private String workflowId;
    private String workflowName;
    private int workflowVersion;

    private String initiatedBy; // userId who started
    private String clientId; // client the process belongs to

    @Builder.Default
    private InstanceStatus status = InstanceStatus.RUNNING;

    // Currently active node(s) — multiple for parallel execution
    @Builder.Default
    private List<String> activeNodeIds = new ArrayList<>();

    private String currentNodeId;

    // Process variables (form data collected across nodes)
    @Builder.Default
    private Map<String, Object> variables = new java.util.HashMap<>();

    // Full execution history
    @Builder.Default
    private List<ExecutionStep> history = new ArrayList<>();

    // Timings
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime dueAt;

    // SLA
    private boolean slaBreached;
    private LocalDateTime slaBreach;

    // Risk & fraud
    private double riskScore;
    private boolean flaggedForReview;

    // Priority
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    // Reference number for client tracking
    private String referenceNumber;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum InstanceStatus {
        RUNNING, COMPLETED, CANCELLED, SUSPENDED, ERROR
    }

    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecutionStep {
        private String nodeId;
        private String nodeName;
        private String performedBy;
        private String action;
        private String comment;
        private Map<String, Object> formData;
        private LocalDateTime timestamp;
        private long durationMs;
    }
}
