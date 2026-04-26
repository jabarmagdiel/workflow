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

@Document(collection = "workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workflow {

    @Id
    private String id;

    @Indexed
    private String name;
    private String description;
    private String category;

    private String createdBy; // userId
    private String ownedBy; // userId or department

    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @Builder.Default
    private int version = 1;

    // JSON representation stored as Map for flexibility
    @Builder.Default
    private List<WorkflowNode> nodes = new ArrayList<>();

    @Builder.Default
    private List<WorkflowEdge> edges = new ArrayList<>();

    // Metadata for versioning
    private String previousVersionId;
    private boolean isCurrentVersion;

    // SLA settings (in hours)
    private Integer defaultSlaHours;

    @Builder.Default
    private Map<String, Object> settings = new java.util.HashMap<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum WorkflowStatus {
        DRAFT, PUBLISHED, DEPRECATED, ARCHIVED
    }
}
