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

@Document(collection = "forms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Form {

    @Id
    private String id;

    private String name;
    private String description;
    private String workflowId;
    private String nodeId;
    private String createdBy;

    @Builder.Default
    private List<FormField> fields = new ArrayList<>();

    @Builder.Default
    private boolean active = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FormField {
        private String id;
        private String name;
        private String label;
        private FieldType type;
        private boolean required;
        private Object defaultValue;
        private List<String> options; // for SELECT type
        private String validationRegex;
        private String placeholder;
        private String helpText;
        private int order;
        private java.util.Map<String, Object> metadata;
    }

    public enum FieldType {
        TEXT, NUMBER, DATE, DATETIME, EMAIL, PHONE,
        TEXTAREA, SELECT, MULTISELECT, CHECKBOX,
        FILE, IMAGE, SIGNATURE
    }
}
