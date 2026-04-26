package com.bpflow.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "voice_commands")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCommand {

    @Id
    private String id;

    private String userId;
    private String sessionId;

    private String rawText; // Transcribed speech
    private String normalizedText; // Cleaned/normalized text
    private String detectedIntent; // e.g. CREATE_NODE, CONNECT_NODES
    private Map<String, Object> entities; // Extracted entities
    private String actionExecuted; // What the system did
    private boolean success;
    private String errorMessage;

    // Context
    private String workflowId;
    private String context; // e.g. DESIGNER, FORM_BUILDER

    // Confidence score from NLP
    private double confidence;

    @CreatedDate
    private LocalDateTime timestamp;
}
