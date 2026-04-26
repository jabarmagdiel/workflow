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
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    private String id;

    private String instanceId;
    private String workflowId;
    private String workflowName;
    private String nodeId;
    private String nodeName;

    private String title;
    private String description;
    private String formId;

    @Indexed
    private String assignedTo; // userId
    private String assignedRole;
    private String createdBy;

    @Builder.Default
    private TaskStatus status = TaskStatus.NEW;

    @Builder.Default
    private Priority priority = Priority.NORMAL;

    // Form data submitted for this task
    @Builder.Default
    private Map<String, Object> formData = new java.util.HashMap<>();

    // Attached files / evidence
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    private String comment;

    // SLA
    private LocalDateTime dueAt;
    private boolean overdue;
    private boolean slaBreached;

    // Timing
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long processingTimeMs;

    // Reassignment history
    @Builder.Default
    private List<ReassignmentRecord> reassignmentHistory = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum TaskStatus {
        NEW, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED, DELEGATED
    }

    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Attachment {
        private String fileName;
        private String fileType;
        private String fileUrl;
        private long fileSize;
        private LocalDateTime uploadedAt;
        private String uploadedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReassignmentRecord {
        private String fromUserId;
        private String toUserId;
        private String reason;
        private LocalDateTime timestamp;
    }
}
