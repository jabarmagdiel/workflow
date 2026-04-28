package com.bpflow.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowNode {

    private String id; // UUID within workflow
    private String label; // Display name
    private NodeType type;

    // Position on canvas (for visual designer)
    private double x;
    private double y;

    // Assignments
    private String assignedRole; // Role that handles this node
    private String assignedUserId; // Specific user (optional)
    private String department;

    // SLA
    private Integer slaHours;

    // Description / instructions for the node
    private String description;

    // Conditional rules: [{field, operator, value, targetNodeId}]
    @Builder.Default
    private List<Map<String, Object>> conditions = new ArrayList<>();

    // Form associated with this node
    private String formId;

    // Metadata
    @Builder.Default
    private Map<String, Object> metadata = new java.util.HashMap<>();

    private boolean startNode;
    private boolean endNode;

    public enum NodeType {
        START, END, TASK, DECISION, PARALLEL_GATEWAY, JOIN_GATEWAY, SUBPROCESS
    }
}
