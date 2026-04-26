package com.bpflow.controller;

import com.bpflow.model.WorkflowInstance;
import com.bpflow.service.WorkflowEngineService;
import com.bpflow.repository.WorkflowInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
public class WorkflowInstanceController {

    private final WorkflowEngineService engineService;
    private final WorkflowInstanceRepository instanceRepository;
    private final com.bpflow.service.ReportService reportService;

    @PostMapping("/start")
    public ResponseEntity<WorkflowInstance> start(@RequestBody Map<String, Object> body,
            @AuthenticationPrincipal String userId) {
        String workflowId = (String) body.get("workflowId");
        String clientId = (String) body.getOrDefault("clientId", userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) body.getOrDefault("variables", Map.of());
        String priority = (String) body.getOrDefault("priority", "NORMAL");

        WorkflowInstance instance = engineService.startInstance(
                workflowId, userId, clientId, vars,
                WorkflowInstance.Priority.valueOf(priority));

        return ResponseEntity.status(201).body(instance);
    }

    @GetMapping
    public ResponseEntity<List<WorkflowInstance>> getAll() {
        return ResponseEntity.ok(instanceRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowInstance> getById(@PathVariable String id) {
        return instanceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my")
    public ResponseEntity<List<WorkflowInstance>> getMyInstances(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(instanceRepository.findByInitiatedBy(userId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<WorkflowInstance>> getActive() {
        return ResponseEntity.ok(instanceRepository.findByStatus(WorkflowInstance.InstanceStatus.RUNNING));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WorkflowInstance> cancel(@PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(engineService.cancelInstance(id, userId, body.get("reason")));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable String id) {
        return instanceRepository.findById(id).map(instance -> {
            byte[] pdf = reportService.generateInstanceAuditPdf(instance);
            String filename = "audit-" + instance.getReferenceNumber() + ".pdf";
            
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdf);
        }).orElse(ResponseEntity.notFound().build());
    }
}
