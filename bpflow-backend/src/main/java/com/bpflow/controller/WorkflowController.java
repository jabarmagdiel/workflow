package com.bpflow.controller;

import com.bpflow.model.Workflow;
import com.bpflow.model.WorkflowNode;
import com.bpflow.model.WorkflowEdge;
import com.bpflow.repository.WorkflowRepository;
import com.bpflow.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowRepository workflowRepository;
    private final AuditLogService auditLog;

    @GetMapping
    public ResponseEntity<List<Workflow>> getAll() {
        return ResponseEntity.ok(workflowRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workflow> getById(@PathVariable String id) {
        return workflowRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> create(@RequestBody Workflow workflow,
            @AuthenticationPrincipal String userId, HttpServletRequest req) {
        workflow.setId(null);
        workflow.setCreatedBy(userId);
        workflow.setStatus(Workflow.WorkflowStatus.DRAFT);
        Workflow saved = workflowRepository.save(workflow);
        auditLog.log(userId, userId, null, "CREATE_WORKFLOW", "WORKFLOW", saved.getId(), true,
                "Workflow creado: " + saved.getName(), req);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> update(@PathVariable String id,
            @RequestBody Workflow workflow) {
        return workflowRepository.findById(id).map(existing -> {
            if (existing.getStatus() == Workflow.WorkflowStatus.PUBLISHED) {
                throw new IllegalStateException("Cannot edit a published workflow. Create a new version.");
            }
            workflow.setId(id);
            workflow.setCreatedBy(existing.getCreatedBy());
            return ResponseEntity.ok(workflowRepository.save(workflow));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> publish(@PathVariable String id,
            @AuthenticationPrincipal String userId, HttpServletRequest req) {
        return workflowRepository.findById(id).map(wf -> {
            validateWorkflow(wf);
            wf.setStatus(Workflow.WorkflowStatus.PUBLISHED);
            Workflow saved = workflowRepository.save(wf);
            auditLog.log(userId, userId, null, "PUBLISH_WORKFLOW", "WORKFLOW", id, true,
                    "Publicado: " + wf.getName(), req);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/new-version")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> newVersion(@PathVariable String id,
            @AuthenticationPrincipal String userId) {
        return workflowRepository.findById(id).map(original -> {
            Workflow newVer = Workflow.builder()
                    .name(original.getName())
                    .description(original.getDescription())
                    .category(original.getCategory())
                    .createdBy(userId)
                    .status(Workflow.WorkflowStatus.DRAFT)
                    .version(original.getVersion() + 1)
                    .nodes(original.getNodes())
                    .edges(original.getEdges())
                    .previousVersionId(original.getId())
                    .defaultSlaHours(original.getDefaultSlaHours())
                    .build();
            return ResponseEntity.status(201).body(workflowRepository.save(newVer));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id,
            @AuthenticationPrincipal String userId, HttpServletRequest req) {
        workflowRepository.findById(id).ifPresent(wf ->
            auditLog.log(userId, userId, null, "DELETE_WORKFLOW", "WORKFLOW", id, true,
                    "Eliminado: " + wf.getName(), req));
        workflowRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── NODE OPERATIONS ─────────────────────────────────────────
    @PostMapping("/{id}/nodes")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> addNode(@PathVariable String id,
            @RequestBody WorkflowNode node) {
        return workflowRepository.findById(id).map(wf -> {
            node.setId(UUID.randomUUID().toString());
            wf.getNodes().add(node);
            return ResponseEntity.ok(workflowRepository.save(wf));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/edges")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> addEdge(@PathVariable String id,
            @RequestBody WorkflowEdge edge) {
        return workflowRepository.findById(id).map(wf -> {
            edge.setId(UUID.randomUUID().toString());
            wf.getEdges().add(edge);
            return ResponseEntity.ok(workflowRepository.save(wf));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> updateNode(@PathVariable String id,
            @PathVariable String nodeId,
            @RequestBody WorkflowNode patch) {
        return workflowRepository.findById(id).map(wf -> {
            wf.getNodes().stream().filter(n -> n.getId().equals(nodeId)).findFirst().ifPresent(n -> {
                if (patch.getLabel()        != null) n.setLabel(patch.getLabel());
                if (patch.getDescription()  != null) n.setDescription(patch.getDescription().isEmpty() ? null : patch.getDescription());
                if (patch.getAssignedRole() != null) n.setAssignedRole(patch.getAssignedRole().isEmpty() ? null : patch.getAssignedRole());
                if (patch.getAssignedUserId() != null) n.setAssignedUserId(patch.getAssignedUserId().isEmpty() ? null : patch.getAssignedUserId());
                if (patch.getSlaHours()     != null) n.setSlaHours(patch.getSlaHours());
                if (patch.getX()            != 0)    n.setX(patch.getX());
                if (patch.getY()            != 0)    n.setY(patch.getY());
                n.setStartNode(patch.isStartNode());
                n.setEndNode(patch.isEndNode());
            });
            return ResponseEntity.ok(workflowRepository.save(wf));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> removeNode(@PathVariable String id,
            @PathVariable String nodeId) {
        return workflowRepository.findById(id).map(wf -> {
            wf.getNodes().removeIf(n -> n.getId().equals(nodeId));
            wf.getEdges().removeIf(e -> e.getSourceNodeId().equals(nodeId) || e.getTargetNodeId().equals(nodeId));
            return ResponseEntity.ok(workflowRepository.save(wf));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/edges/{edgeId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> removeEdge(@PathVariable String id,
            @PathVariable String edgeId) {
        return workflowRepository.findById(id).map(wf -> {
            wf.getEdges().removeIf(e -> e.getId().equals(edgeId));
            return ResponseEntity.ok(workflowRepository.save(wf));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/edges/{edgeId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Workflow> updateEdge(@PathVariable String id,
            @PathVariable String edgeId,
            @RequestBody com.bpflow.model.WorkflowEdge patch) {
        return workflowRepository.findById(id).map(wf -> {
            wf.getEdges().stream()
                .filter(e -> e.getId().equals(edgeId))
                .findFirst()
                .ifPresent(e -> {
                    if (patch.getLabel() != null) e.setLabel(patch.getLabel());
                    if (patch.getCondition() != null) e.setCondition(patch.getCondition());
                });
            return ResponseEntity.ok(workflowRepository.save(wf));
        }).orElse(ResponseEntity.notFound().build());
    }

    private void validateWorkflow(Workflow wf) {
        long startNodes = wf.getNodes().stream().filter(WorkflowNode::isStartNode).count();
        long endNodes = wf.getNodes().stream().filter(WorkflowNode::isEndNode).count();

        log.info("Validating workflow '{}': Found {} start nodes and {} end nodes",
                wf.getName(), startNodes, endNodes);

        if (startNodes != 1)
            throw new IllegalStateException("Workflow must have exactly 1 start node. Found: " + startNodes);
        if (endNodes < 1)
            throw new IllegalStateException("Workflow must have at least 1 end node. Found: " + endNodes);
    }
}

