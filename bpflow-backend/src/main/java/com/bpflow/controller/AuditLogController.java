package com.bpflow.controller;

import com.bpflow.model.*;
import com.bpflow.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AuditLogController {

    private final AuditLogRepository         auditLogRepo;
    private final WorkflowRepository         workflowRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final TaskRepository             taskRepo;

    // ─── AUDIT LOGS ─────────────────────────────────────────────────────

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "50")   int size,
            @RequestParam(required = false)      String userId,
            @RequestParam(required = false)      String action,
            @RequestParam(required = false)      String resourceType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        // Use findAll and filter in memory (simple implementation)
        List<AuditLog> all = auditLogRepo.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));

        // Apply filters
        List<AuditLog> filtered = all.stream()
            .filter(l -> userId       == null || userId.isBlank()       || userId.equals(l.getUserId()))
            .filter(l -> action       == null || action.isBlank()       || action.equalsIgnoreCase(l.getAction()))
            .filter(l -> resourceType == null || resourceType.isBlank() || resourceType.equalsIgnoreCase(l.getResourceType()))
            .filter(l -> from         == null || (l.getTimestamp() != null && !l.getTimestamp().isBefore(from)))
            .filter(l -> to           == null || (l.getTimestamp() != null && !l.getTimestamp().isAfter(to)))
            .toList();

        // Manual pagination
        int total  = filtered.size();
        int start  = Math.min(page * size, total);
        int end    = Math.min(start + size, total);
        List<AuditLog> pageContent = filtered.subList(start, end);

        return ResponseEntity.ok(Map.of(
            "content",       pageContent,
            "totalElements", total,
            "totalPages",    (int) Math.ceil((double) total / size),
            "page",          page,
            "size",          size
        ));
    }

    @GetMapping("/logs/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<AuditLog> all = auditLogRepo.findAll();

        // Count by action
        Map<String, Long> byAction = new LinkedHashMap<>();
        all.stream().collect(java.util.stream.Collectors.groupingBy(
            l -> l.getAction() != null ? l.getAction() : "UNKNOWN",
            java.util.stream.Collectors.counting()
        )).entrySet().stream()
          .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
          .forEach(e -> byAction.put(e.getKey(), e.getValue()));

        // Count by resource type
        Map<String, Long> byResource = new LinkedHashMap<>();
        all.stream().collect(java.util.stream.Collectors.groupingBy(
            l -> l.getResourceType() != null ? l.getResourceType() : "NONE",
            java.util.stream.Collectors.counting()
        )).entrySet().stream()
          .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
          .forEach(e -> byResource.put(e.getKey(), e.getValue()));

        long successCount = all.stream().filter(AuditLog::isSuccess).count();
        long failCount    = all.size() - successCount;

        return ResponseEntity.ok(Map.of(
            "total",      all.size(),
            "success",    successCount,
            "failures",   failCount,
            "byAction",   byAction,
            "byResource", byResource
        ));
    }

    // ─── BACKUP / EXPORT ────────────────────────────────────────────────

    @GetMapping("/backup/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportBackup() throws IOException {
        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("exportedAt",  LocalDateTime.now().toString());
        backup.put("version",     "1.0");
        backup.put("workflows",   workflowRepo.findAll());
        backup.put("instances",   instanceRepo.findAll());
        backup.put("tasks",       taskRepo.findAll());
        backup.put("auditLogs",   auditLogRepo.findAll());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        byte[] json = mapper.writeValueAsBytes(backup);
        String filename = "bpflow-backup-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            + ".json";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json);
    }

    @GetMapping("/backup/stats")
    public ResponseEntity<Map<String, Object>> backupStats() {
        return ResponseEntity.ok(Map.of(
            "workflows",  workflowRepo.count(),
            "instances",  instanceRepo.count(),
            "tasks",      taskRepo.count(),
            "auditLogs",  auditLogRepo.count(),
            "generatedAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/backup/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importBackup(
            @RequestParam("file") MultipartFile file) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        Map<?, ?> backup = mapper.readValue(file.getInputStream(), Map.class);

        // Restore workflows
        int wfCount = 0;
        if (backup.containsKey("workflows")) {
            List<?> wfs = (List<?>) backup.get("workflows");
            for (Object o : wfs) {
                Workflow wf = mapper.convertValue(o, Workflow.class);
                if (!workflowRepo.existsById(wf.getId())) {
                    workflowRepo.save(wf);
                    wfCount++;
                }
            }
        }

        return ResponseEntity.ok(Map.of(
            "message",           "Backup importado exitosamente",
            "workflowsRestored", wfCount,
            "restoredAt",        LocalDateTime.now().toString()
        ));
    }
}
