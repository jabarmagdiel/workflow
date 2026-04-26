package com.bpflow.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    private String id;

    private String userId;
    private String userEmail;
    private String userRole;

    private String action; // e.g. LOGIN, LOGOUT, CREATE_WORKFLOW
    private String resourceType; // e.g. WORKFLOW, TASK, USER
    private String resourceId;

    private String ipAddress;
    private String userAgent;

    private boolean success;
    private String detail;

    @CreatedDate
    private LocalDateTime timestamp;
}
