package com.bpflow.service;

import com.bpflow.model.Notification;
import com.bpflow.model.Task;
import com.bpflow.model.WorkflowInstance;
import com.bpflow.repository.NotificationRepository;
import com.bpflow.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler wsHandler;
    private final FcmService fcmService;
    private final com.bpflow.repository.UserRepository userRepository;

    private void sendToUser(String userId, Notification n) {
        wsHandler.sendToUser(userId, n);
        
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFcmToken() != null) {
                fcmService.sendPushNotification(user.getFcmToken(), n.getTitle(), n.getMessage());
            }
        });
    }

    public void notifyTaskAssigned(Task task, String recipientId) {
        if (recipientId == null)
            return;
        Notification n = Notification.builder()
                .recipientId(recipientId)
                .type(Notification.NotificationType.TASK_ASSIGNED)
                .title("Nueva tarea asignada")
                .message("Tienes una nueva tarea: " + task.getTitle())
                .relatedType("TASK")
                .relatedId(task.getId())
                .build();
        notificationRepository.save(n);
        sendToUser(recipientId, n);
    }

    public void notifyInstanceCompleted(WorkflowInstance instance, String userId) {
        Notification n = Notification.builder()
                .recipientId(instance.getInitiatedBy())
                .type(Notification.NotificationType.INSTANCE_COMPLETED)
                .title("Proceso completado")
                .message("Tu trámite " + instance.getReferenceNumber() + " ha sido completado.")
                .relatedType("INSTANCE")
                .relatedId(instance.getId())
                .build();
        notificationRepository.save(n);
        sendToUser(instance.getInitiatedBy(), n);
    }

    public void notifyInstanceCancelled(WorkflowInstance instance, String userId) {
        Notification n = Notification.builder()
                .recipientId(instance.getInitiatedBy())
                .type(Notification.NotificationType.INSTANCE_CANCELLED)
                .title("Proceso cancelado")
                .message("Tu trámite " + instance.getReferenceNumber() + " ha sido cancelado.")
                .relatedType("INSTANCE")
                .relatedId(instance.getId())
                .build();
        notificationRepository.save(n);
        sendToUser(instance.getInitiatedBy(), n);
    }

    public void notifyFraudAlert(String adminId, String alertMessage) {
        Notification n = Notification.builder()
                .recipientId(adminId)
                .type(Notification.NotificationType.FRAUD_ALERT)
                .title("⚠️ Alerta de Fraude")
                .message(alertMessage)
                .relatedType("FRAUD_ALERT")
                .build();
        notificationRepository.save(n);
        sendToUser(adminId, n);
    }

    public void notifyTest(String userId, Notification n) {
        sendToUser(userId, n);
    }

    public void broadcastEvent(String eventType, Object data) {
        wsHandler.broadcast(Map.of(
            "event", eventType,
            "data", data,
            "timestamp", LocalDateTime.now()
        ));
    }

    public void sendPasswordResetEmail(String email, String token) {
        // In production: integrate with mail service / SendGrid / SES
        log.info("Password reset link: http://localhost:4200/reset-password?token={}", token);
    }
}
