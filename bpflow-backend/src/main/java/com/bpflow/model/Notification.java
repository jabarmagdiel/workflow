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

@Document(collection = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    private String recipientId; // userId

    private NotificationType type;
    private String title;
    private String message;

    // Reference to the related entity
    private String relatedType; // TASK, INSTANCE, FRAUD_ALERT, etc.
    private String relatedId;

    @Builder.Default
    private boolean read = false;

    private LocalDateTime readAt;

    // WebSocket delivery tracking
    @Builder.Default
    private boolean delivered = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum NotificationType {
        TASK_ASSIGNED,
        TASK_OVERDUE,
        TASK_COMPLETED,
        INSTANCE_STARTED,
        INSTANCE_COMPLETED,
        INSTANCE_CANCELLED,
        FRAUD_ALERT,
        SLA_WARNING,
        SLA_BREACH,
        SYSTEM_MESSAGE
    }
}
