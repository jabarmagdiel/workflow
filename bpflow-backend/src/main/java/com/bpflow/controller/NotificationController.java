package com.bpflow.controller;

import com.bpflow.model.Notification;
import com.bpflow.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final com.bpflow.service.NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> getMyNotifications(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal String userId) {
        long count = notificationRepository.countByRecipientIdAndReadFalse(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id,
            @AuthenticationPrincipal String userId) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getRecipientId().equals(userId)) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        });
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal String userId) {
        notificationRepository.findByRecipientIdAndReadFalse(userId).forEach(n -> {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        });
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/test")
    public ResponseEntity<Void> sendTest(@AuthenticationPrincipal String userId) {
        Notification n = Notification.builder()
                .recipientId(userId)
                .title("Prueba en Tiempo Real")
                .message("¡Hola! Si ves esto, los WebSockets están funcionando correctamente. 🚀")
                .type(Notification.NotificationType.SYSTEM_MESSAGE)
                .createdAt(LocalDateTime.now())
                .build();
        
        notificationRepository.save(n);
        notificationService.notifyTest(userId, n);
        return ResponseEntity.ok().build();
    }
}
