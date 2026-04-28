package com.bpflow.service;

import com.bpflow.model.*;
import com.bpflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.core.ParameterizedTypeReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private final FraudAlertRepository fraudAlertRepository;
    private final org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    // ─── START INSTANCE ──────────────────────────────────────────
    public WorkflowInstance startInstance(String workflowId, String userId,
            String clientId, Map<String, Object> initialVariables,
            WorkflowInstance.Priority priority) {

        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

        if (wf.getStatus() != Workflow.WorkflowStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot start a non-published workflow");
        }

        // Find start node
        WorkflowNode startNode = wf.getNodes().stream()
                .filter(n -> n.getType() == WorkflowNode.NodeType.START || n.isStartNode())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Workflow has no start node"));

        // Find first task node after start
        WorkflowNode firstTask = getNextNodes(wf, startNode.getId()).stream()
                .findFirst()
                .orElse(null);

        String refNumber = generateReferenceNumber(wf.getName());

        // Fetch client details
        String clientName = "Consumidor Final";
        String clientEmail = "";
        if (clientId != null) {
            User client = userRepository.findById(clientId).orElse(null);
            if (client != null) {
                clientName = client.getFirstName() + " " + client.getLastName();
                clientEmail = client.getEmail();
            }
        }

        WorkflowInstance instance = WorkflowInstance.builder()
                .workflowId(workflowId)
                .workflowName(wf.getName())
                .workflowVersion(wf.getVersion())
                .initiatedBy(userId)
                .clientId(clientId)
                .clientName(clientName)
                .clientEmail(clientEmail)
                .status(WorkflowInstance.InstanceStatus.RUNNING)
                .activeNodeIds(firstTask != null ? List.of(firstTask.getId()) : List.of())
                .currentNodeId(firstTask != null ? firstTask.getId() : null)
                .currentNodeName(firstTask != null ? firstTask.getLabel() : "Inicio")
                .variables(initialVariables != null ? new HashMap<>(initialVariables) : new HashMap<>())
                .priority(priority != null ? priority : WorkflowInstance.Priority.NORMAL)
                .referenceNumber(refNumber)
                .startedAt(LocalDateTime.now())
                .build();

        // Set SLA
        if (wf.getDefaultSlaHours() != null) {
            instance.setDueAt(LocalDateTime.now().plusHours(wf.getDefaultSlaHours()));
        }

        instanceRepository.save(instance);

        // Add to history
        addHistoryStep(instance, startNode.getId(), startNode.getLabel(), userId, "STARTED", "Instance started", null,
                0);

        // Create first task(s)
        if (firstTask != null) {
            if (firstTask.getType() == WorkflowNode.NodeType.PARALLEL_GATEWAY) {
                createParallelTasks(wf, instance, firstTask, userId);
            } else {
                createTask(wf, instance, firstTask, userId);
            }
        }

        instanceRepository.save(instance);
        log.info("Started workflow instance {} for workflow {}", instance.getId(), workflowId);
        
        notificationService.broadcastEvent("INSTANCE_STARTED", instance);
        notificationService.broadcastEvent("DASHBOARD_UPDATE", null);
        
        return instance;
    }

    // ─── COMPLETE TASK ───────────────────────────────────────────
    public WorkflowInstance completeTask(String taskId, String userId,
            Map<String, Object> formData, String action, String comment) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        String userRole = getUserRole(userId);
        boolean isAdmin = "ADMIN".equals(userRole);
        boolean isAssigned = task.getAssignedTo() != null && task.getAssignedTo().equals(userId);
        boolean hasRole = task.getAssignedRole() != null && task.getAssignedRole().equals(userRole);

        if (!isAdmin && !isAssigned && !hasRole) {
            throw new SecurityException("User is not authorized to complete this task");
        }

        WorkflowInstance instance = instanceRepository.findById(task.getInstanceId())
                .orElseThrow(() -> new RuntimeException("Instance not found"));

        Workflow wf = workflowRepository.findById(instance.getWorkflowId())
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        // Mark task done
        task.setStatus("APPROVE".equals(action) ? Task.TaskStatus.COMPLETED : Task.TaskStatus.REJECTED);
        task.setFormData(formData != null ? formData : new HashMap<>());
        task.setComment(comment);
        task.setCompletedAt(LocalDateTime.now());
        if (task.getStartedAt() != null) {
            task.setProcessingTimeMs(
                    java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis());
        }
        taskRepository.save(task);

        // Merge form data into instance variables
        if (formData != null) {
            instance.getVariables().putAll(formData);
        }

        // Record history
        long duration = (task.getStartedAt() != null)
                ? java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis()
                : 0;
        addHistoryStep(instance, task.getNodeId(), task.getNodeName(), userId,
                action, comment, formData, duration);

        // Determine next nodes
        List<WorkflowNode> nextNodes = resolveNextNodes(wf, task.getNodeId(),
                action, instance.getVariables());

        if (nextNodes.isEmpty()) {
            // Workflow complete
            completeInstance(instance, userId);
        } else {
            // Check for parallel completion
            List<String> remainingActive = new ArrayList<>(instance.getActiveNodeIds());
            remainingActive.remove(task.getNodeId());

            List<String> newActiveNodes = new ArrayList<>();

            for (WorkflowNode nextNode : nextNodes) {
                if (nextNode.getType() == WorkflowNode.NodeType.END || nextNode.isEndNode()) {
                    if (remainingActive.isEmpty()) {
                        completeInstance(instance, userId);
                        return instanceRepository.save(instance);
                    }
                } else if (nextNode.getType() == WorkflowNode.NodeType.PARALLEL_GATEWAY) {
                    createParallelTasks(wf, instance, nextNode, userId);
                    newActiveNodes.addAll(getNextNodes(wf, nextNode.getId())
                            .stream().map(WorkflowNode::getId).toList());
                } else if (nextNode.getType() == WorkflowNode.NodeType.JOIN_GATEWAY) {
                    if (remainingActive.isEmpty()) {
                        List<WorkflowNode> afterJoin = getNextNodes(wf, nextNode.getId());
                        for (WorkflowNode n : afterJoin) {
                            createTask(wf, instance, n, userId);
                            newActiveNodes.add(n.getId());
                        }
                    }
                } else if (nextNode.getType() == WorkflowNode.NodeType.DECISION) {
                    // Atraviesa el gateway de decisión y busca el siguiente nodo real basado en la acción anterior
                    List<WorkflowNode> nodesAfterGateway = resolveNextNodes(wf, nextNode.getId(), action, instance.getVariables());
                    for (WorkflowNode realNextNode : nodesAfterGateway) {
                        createTask(wf, instance, realNextNode, userId);
                        newActiveNodes.add(realNextNode.getId());
                    }
                } else {
                    createTask(wf, instance, nextNode, userId);
                    newActiveNodes.add(nextNode.getId());
                    instance.setCurrentNodeId(nextNode.getId());
                    instance.setCurrentNodeName(nextNode.getLabel());
                }
            }

            remainingActive.addAll(newActiveNodes);
            instance.setActiveNodeIds(remainingActive);
            instanceRepository.save(instance);
        }

        notificationService.broadcastEvent("TASK_COMPLETED", task);
        notificationService.broadcastEvent("INSTANCE_UPDATED", instance);
        notificationService.broadcastEvent("DASHBOARD_UPDATE", null);

        // --- Trigger AI Fraud Analysis ---
        analyzeFraudAsync(instance, task, userId);

        return instance;
    }

    private void analyzeFraudAsync(WorkflowInstance instance, Task task, String userId) {
        // En un escenario real esto sería @Async o vía Kafka/RabbitMQ
        // Por simplicidad, llamamos al microservicio FastAPI de forma no bloqueante
        webClientBuilder.build()
                .post()
                .uri("http://ai-service:8000/fraud/analyze-instance")
                .bodyValue(Map.of(
                        "instance_id", instance.getId(),
                        "history", instance.getHistory()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .subscribe(result -> {
                    Double riskScore = (Double) result.get("risk_score");
                    log.info("AI Analysis result for {}: score={}", instance.getId(), riskScore);
                    if (riskScore != null && riskScore > 0.6) {
                        fraudAlertRepository.save(FraudAlert.builder()
                                .instanceId(instance.getId())
                                .workflowId(instance.getWorkflowId())
                                .taskId(task.getId())
                                .userId(userId)
                                .alertType(FraudAlert.AlertType.ANOMALOUS_TIMING)
                                .description(result.get("indicators") instanceof List<?> list && !list.isEmpty()
                                        ? String.valueOf(list.get(0))
                                        : "Anomalous activity detected")
                                .riskScore(riskScore)
                                .severity(riskScore > 0.8 ? FraudAlert.AlertSeverity.CRITICAL
                                        : FraudAlert.AlertSeverity.HIGH)
                                .status(FraudAlert.AlertStatus.OPEN)
                                .build());
                        log.warn("🚨 FRAUD ALERT generated for instance {}", instance.getId());
                    }
                }, err -> log.error("Error calling AI service: {}", err.getMessage()));
    }

    // ─── CANCEL INSTANCE ─────────────────────────────────────────
    public WorkflowInstance cancelInstance(String instanceId, String userId, String reason) {
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found"));

        instance.setStatus(WorkflowInstance.InstanceStatus.CANCELLED);
        instance.setCompletedAt(LocalDateTime.now());

        // Cancel all pending tasks
        taskRepository.findByInstanceId(instanceId).stream()
                .filter(t -> t.getStatus() == Task.TaskStatus.NEW || t.getStatus() == Task.TaskStatus.IN_PROGRESS)
                .forEach(t -> {
                    t.setStatus(Task.TaskStatus.CANCELLED);
                    taskRepository.save(t);
                });

        notificationService.notifyInstanceCancelled(instance, userId);
        notificationService.broadcastEvent("INSTANCE_CANCELLED", instance);
        notificationService.broadcastEvent("DASHBOARD_UPDATE", null);
        return instanceRepository.save(instance);
    }

    // ─── REASSIGN TASK ───────────────────────────────────────────
    public Task reassignTask(String taskId, String fromUserId, String toUserId, String reason) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Track reassignment for fraud detection
        task.getReassignmentHistory().add(Task.ReassignmentRecord.builder()
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build());

        task.setAssignedTo(toUserId);
        taskRepository.save(task);

        notificationService.notifyTaskAssigned(task, toUserId);
        notificationService.broadcastEvent("TASK_REASSIGNED", task);
        return task;
    }

    // ─── INTERNAL HELPERS ────────────────────────────────────────
    private void completeInstance(WorkflowInstance instance, String userId) {
        instance.setStatus(WorkflowInstance.InstanceStatus.COMPLETED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setActiveNodeIds(new ArrayList<>());

        if (instance.getDueAt() != null && LocalDateTime.now().isAfter(instance.getDueAt())) {
            instance.setSlaBreached(true);
            instance.setSlaBreach(instance.getCompletedAt());
        }

        notificationService.notifyInstanceCompleted(instance, userId);
        log.info("Workflow instance {} completed", instance.getId());
    }

    private void createTask(Workflow wf, WorkflowInstance instance, WorkflowNode node, String triggeredBy) {
        String assignedUserId = resolveAssignment(node);

        Task task = Task.builder()
                .instanceId(instance.getId())
                .workflowId(instance.getWorkflowId())
                .workflowName(wf.getName())
                .nodeId(node.getId())
                .nodeName(node.getLabel())
                .title(node.getLabel())
                .description("Complete task: " + node.getLabel())
                .formId(node.getFormId())
                .assignedTo(assignedUserId)
                .assignedRole(node.getAssignedRole())
                .createdBy(triggeredBy)
                .status(Task.TaskStatus.NEW)
                .priority(Task.Priority.valueOf(instance.getPriority().name()))
                .build();

        // SLA per node
        if (node.getSlaHours() != null) {
            task.setDueAt(LocalDateTime.now().plusHours(node.getSlaHours()));
        }

        taskRepository.save(task);
        notificationService.notifyTaskAssigned(task, assignedUserId);
    }

    private void createParallelTasks(Workflow wf, WorkflowInstance instance,
            WorkflowNode gateway, String triggeredBy) {
        List<WorkflowNode> parallelNodes = getNextNodes(wf, gateway.getId());
        parallelNodes.forEach(node -> createTask(wf, instance, node, triggeredBy));
        List<String> activeNodes = new ArrayList<>(instance.getActiveNodeIds());
        parallelNodes.stream().map(WorkflowNode::getId).forEach(activeNodes::add);
        instance.setActiveNodeIds(activeNodes);
    }

    private List<WorkflowNode> resolveNextNodes(Workflow wf, String currentNodeId,
            String action, Map<String, Object> variables) {
        List<WorkflowNode> result = new ArrayList<>();

        // Find edges from current node
        List<WorkflowEdge> outEdges = wf.getEdges().stream()
                .filter(e -> e.getSourceNodeId().equals(currentNodeId))
                .toList();

        for (WorkflowEdge edge : outEdges) {
            // Evaluate condition
            if (edge.getCondition() == null || evaluateCondition(edge.getCondition(), action, variables)) {
                wf.getNodes().stream()
                        .filter(n -> n.getId().equals(edge.getTargetNodeId()))
                        .findFirst()
                        .ifPresent(result::add);
                if (edge.getCondition() != null)
                    break; // First matching condition wins
            }
        }

        // Fallback: use DEFAULT edges
        if (result.isEmpty()) {
            outEdges.stream()
                    .filter(e -> e.getType() == WorkflowEdge.EdgeType.DEFAULT)
                    .findFirst()
                    .ifPresent(e -> wf.getNodes().stream()
                            .filter(n -> n.getId().equals(e.getTargetNodeId()))
                            .findFirst()
                            .ifPresent(result::add));
        }

        return result;
    }

    private boolean evaluateCondition(String condition, String action, Map<String, Object> variables) {
        // Simple evaluator bilingüe
        if (condition.equalsIgnoreCase("approved") || 
            condition.equalsIgnoreCase("APPROVE") || 
            condition.equalsIgnoreCase("Aprobado")) {
            return "APPROVE".equals(action);
        }
        if (condition.equalsIgnoreCase("rejected") || 
            condition.equalsIgnoreCase("REJECT") || 
            condition.equalsIgnoreCase("Rechazado")) {
            return "REJECT".equals(action);
        }
        // Variable-based: "status == approved"
        if (condition.contains("==")) {
            String[] parts = condition.split("==");
            String key = parts[0].trim();
            String expected = parts[1].trim().replace("'", "").replace("\"", "");
            Object actual = variables.get(key);
            return expected.equals(String.valueOf(actual));
        }
        return true;
    }

    private List<WorkflowNode> getNextNodes(Workflow wf, String nodeId) {
        return wf.getEdges().stream()
                .filter(e -> e.getSourceNodeId().equals(nodeId))
                .map(e -> wf.getNodes().stream()
                        .filter(n -> n.getId().equals(e.getTargetNodeId()))
                        .findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private String resolveAssignment(WorkflowNode node) {
        if (node.getAssignedUserId() != null)
            return node.getAssignedUserId();
        if (node.getAssignedRole() != null) {
            return userRepository.findAll().stream()
                    .filter(u -> u.getRoles().contains(node.getAssignedRole()))
                    .map(User::getId)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String getUserRole(String userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRoles().stream().findFirst().orElse(""))
                .orElse("");
    }

    private void addHistoryStep(WorkflowInstance instance, String nodeId, String nodeName,
            String performer, String action, String comment,
            Map<String, Object> formData, long duration) {
        instance.getHistory().add(WorkflowInstance.ExecutionStep.builder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .performedBy(performer)
                .action(action)
                .comment(comment)
                .formData(formData)
                .timestamp(LocalDateTime.now())
                .durationMs(duration)
                .build());
    }

    private String generateReferenceNumber(String workflowName) {
        String stripped = workflowName.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String prefix = stripped.substring(0, Math.min(3, stripped.length()));
        if (prefix.isEmpty()) prefix = "WF";
        return prefix + "-" + System.currentTimeMillis() % 1000000;
    }
}
