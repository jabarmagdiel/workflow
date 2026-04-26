package com.bpflow.controller;

import com.bpflow.model.FraudAlert;
import com.bpflow.repository.FraudAlertRepository;
import com.bpflow.repository.WorkflowInstanceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class FraudController {

    private final FraudAlertRepository fraudAlertRepository;
    private final WorkflowInstanceRepository instanceRepository;

    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlert>> getAlerts(
            @RequestParam(required = false) FraudAlert.AlertStatus status) {
        if (status != null)
            return ResponseEntity.ok(fraudAlertRepository.findByStatus(status));
        return ResponseEntity.ok(fraudAlertRepository.findAll());
    }

    @GetMapping("/alerts/open")
    public ResponseEntity<List<FraudAlert>> getOpenAlerts() {
        return ResponseEntity.ok(fraudAlertRepository.findByStatus(FraudAlert.AlertStatus.OPEN));
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<FraudAlert> getAlert(@PathVariable String id) {
        return fraudAlertRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/alerts/{id}/review")
    public ResponseEntity<FraudAlert> reviewAlert(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        return updateStatus(id, body, userId);
    }

    @PatchMapping("/alerts/{id}/status")
    public ResponseEntity<FraudAlert> updateAlertStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        return updateStatus(id, body, userId);
    }

    private ResponseEntity<FraudAlert> updateStatus(String id, Map<String, String> body, String userId) {
        return fraudAlertRepository.findById(id).map(alert -> {
            alert.setStatus(FraudAlert.AlertStatus.valueOf(body.getOrDefault("status", "UNDER_REVIEW")));
            alert.setReviewedBy(userId);
            alert.setReviewedAt(LocalDateTime.now());
            alert.setReviewNotes(body.get("notes"));
            return ResponseEntity.ok(fraudAlertRepository.save(alert));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "open", fraudAlertRepository.countByStatus(FraudAlert.AlertStatus.OPEN),
                "underReview", fraudAlertRepository.countByStatus(FraudAlert.AlertStatus.UNDER_REVIEW),
                "resolved", fraudAlertRepository.countByStatus(FraudAlert.AlertStatus.RESOLVED),
                "flaggedInstances", instanceRepository.findByFlaggedForReview(true).stream().count()));
    }
}
