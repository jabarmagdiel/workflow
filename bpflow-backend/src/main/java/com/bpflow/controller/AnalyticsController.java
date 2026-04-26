package com.bpflow.controller;

import com.bpflow.repository.TaskRepository;
import com.bpflow.repository.WorkflowInstanceRepository;
import com.bpflow.repository.WorkflowRepository;
import com.bpflow.repository.UserRepository;
import com.bpflow.model.Task;
import com.bpflow.model.WorkflowInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AnalyticsController {

        private final WorkflowRepository workflowRepository;
        private final WorkflowInstanceRepository instanceRepository;
        private final TaskRepository taskRepository;
        private final UserRepository userRepository;

        @GetMapping("/summary")
        public ResponseEntity<Map<String, Object>> getSummary() {
                long activeInstances = instanceRepository.findByStatus(WorkflowInstance.InstanceStatus.RUNNING).size();
                long pendingTasks = taskRepository.countByStatus(Task.TaskStatus.NEW);
                long completedToday = taskRepository.countByStatus(Task.TaskStatus.COMPLETED); // Simplificado
                long fraudAlerts = 0; // Placeholder o integración con FraudAlertRepository si existe

                return ResponseEntity.ok(Map.of(
                                "activeInstances", activeInstances,
                                "pendingTasks", pendingTasks,
                                "completedToday", completedToday,
                                "fraudAlerts", fraudAlerts));
        }

        @GetMapping("/dashboard")
        public ResponseEntity<Map<String, Object>> getDashboard() {
                long totalWorkflows = workflowRepository.count();

                long runningInstances = instanceRepository.findByStatus(WorkflowInstance.InstanceStatus.RUNNING).size();
                long completedInstances = instanceRepository.findByStatus(WorkflowInstance.InstanceStatus.COMPLETED)
                                .size();
                long cancelledInstances = instanceRepository.findByStatus(WorkflowInstance.InstanceStatus.CANCELLED)
                                .size();
                long newTasks = taskRepository.countByStatus(Task.TaskStatus.NEW);
                long inProgressTasks = taskRepository.countByStatus(Task.TaskStatus.IN_PROGRESS);
                long completedTasks = taskRepository.countByStatus(Task.TaskStatus.COMPLETED);
                long totalUsers = userRepository.count();

                return ResponseEntity.ok(Map.of(
                                "workflows", totalWorkflows,
                                "instances", Map.of(
                                                "running", runningInstances,
                                                "completed", completedInstances,
                                                "cancelled", cancelledInstances),
                                "tasks", Map.of(
                                                "new", newTasks,
                                                "inProgress", inProgressTasks,
                                                "completed", completedTasks),
                                "totalUsers", totalUsers));
        }

        @GetMapping("/workflow/{workflowId}/performance")
        public ResponseEntity<Map<String, Object>> getWorkflowPerformance(@PathVariable String workflowId) {
                var instances = instanceRepository.findByWorkflowId(workflowId);
                long total = instances.size();
                long completed = instances.stream()
                                .filter(i -> i.getStatus() == WorkflowInstance.InstanceStatus.COMPLETED).count();
                long slaBreached = instances.stream()
                                .filter(WorkflowInstance::isSlaBreached).count();

                double avgDuration = instances.stream()
                                .filter(i -> i.getStartedAt() != null && i.getCompletedAt() != null)
                                .mapToLong(i -> java.time.Duration.between(i.getStartedAt(), i.getCompletedAt())
                                                .toHours())
                                .average()
                                .orElse(0);

                return ResponseEntity.ok(Map.of(
                                "workflowId", workflowId,
                                "totalInstances", total,
                                "completedInstances", completed,
                                "completionRate", total > 0 ? (double) completed / total * 100 : 0,
                                "slaBreached", slaBreached,
                                "avgDurationHours", avgDuration));
        }
}
