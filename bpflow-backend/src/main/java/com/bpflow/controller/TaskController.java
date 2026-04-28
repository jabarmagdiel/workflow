package com.bpflow.controller;

import com.bpflow.model.Task;
import com.bpflow.repository.TaskRepository;
import com.bpflow.service.WorkflowEngineService;
import com.bpflow.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskRepository taskRepository;
    private final WorkflowEngineService engineService;
    private final AuditLogService auditLog;
    private final com.bpflow.service.FileStorageService storageService;

    @GetMapping
    public ResponseEntity<List<Task>> getAll() {
        return ResponseEntity.ok(taskRepository.findAll());
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<Task> uploadAttachment(@PathVariable String id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal String userId) {
        return taskRepository.findById(id).map(task -> {
            String fileUrl = storageService.store(file);
            Task.Attachment attachment = Task.Attachment.builder()
                    .fileName(file.getOriginalFilename())
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .fileUrl(fileUrl)
                    .uploadedBy(userId)
                    .uploadedAt(java.time.LocalDateTime.now())
                    .build();
            
            task.getAttachments().add(attachment);
            return ResponseEntity.ok(taskRepository.save(task));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(@PathVariable String filename) {
        try {
            java.nio.file.Path file = java.nio.file.Paths.get("uploads").resolve(filename);
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .contentType(org.springframework.http.MediaType.parseMediaType(java.nio.file.Files.probeContentType(file)))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/by-instance/{instanceId}")
    public ResponseEntity<List<Task>> getByInstance(@PathVariable String instanceId) {
        return ResponseEntity.ok(taskRepository.findByInstanceId(instanceId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Task>> getMyTasks(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(taskRepository.findByAssignedTo(userId));
    }

    @GetMapping("/my/pending")
    public ResponseEntity<List<Task>> getMyPendingTasks(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(taskRepository.findByAssignedToAndStatus(userId, Task.TaskStatus.NEW));
    }

    @GetMapping("/my/in-progress")
    public ResponseEntity<List<Task>> getMyInProgressTasks(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(taskRepository.findByAssignedToAndStatus(userId, Task.TaskStatus.IN_PROGRESS));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getById(@PathVariable String id) {
        return taskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Task> startTask(@PathVariable String id,
            @AuthenticationPrincipal String userId, HttpServletRequest req) {
        return taskRepository.findById(id).map(task -> {
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
            task.setStartedAt(java.time.LocalDateTime.now());
            Task saved = taskRepository.save(task);
            auditLog.log(userId, userId, null, "TASK_STARTED", "TASK", id, true,
                    "Tarea iniciada: " + task.getTitle(), req);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @RequestMapping(value = "/{id}/complete", method = { RequestMethod.POST, RequestMethod.PATCH })
    public ResponseEntity<Object> completeTask(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {

        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) body.getOrDefault("formData", Map.of());
        String action  = (String) body.getOrDefault("action",  "APPROVE");
        String comment = (String) body.get("comment");

        var instance = engineService.completeTask(id, userId, formData, action, comment);
        auditLog.log(userId, userId, null, "TASK_COMPLETED", "TASK", id, true,
                "Acción: " + action + (comment != null ? " | " + comment : ""), req);
        return ResponseEntity.ok(instance);
    }

    @PostMapping("/{id}/reassign")
    public ResponseEntity<Task> reassign(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {

        return ResponseEntity.ok(engineService.reassignTask(
                id, userId, body.get("toUserId"), body.get("reason")));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "new", taskRepository.countByStatus(Task.TaskStatus.NEW),
                "inProgress", taskRepository.countByStatus(Task.TaskStatus.IN_PROGRESS),
                "completed", taskRepository.countByStatus(Task.TaskStatus.COMPLETED)));
    }
}
