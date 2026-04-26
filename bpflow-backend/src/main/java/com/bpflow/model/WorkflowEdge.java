package com.bpflow.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEdge {

    private String id; // UUID within workflow
    private String sourceNodeId;
    private String targetNodeId;
    private String label; // Optional label

    // Condition expression (for decision gateways)
    private String condition; // e.g. "status == 'approved'"

    // Edge type
    private EdgeType type;

    @Builder.Default
    private Map<String, Object> metadata = new java.util.HashMap<>();

    public enum EdgeType {
        SEQUENCE, CONDITIONAL, DEFAULT
    }
}
