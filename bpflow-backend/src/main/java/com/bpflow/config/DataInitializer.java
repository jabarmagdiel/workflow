package com.bpflow.config;

import com.bpflow.model.*;
import com.bpflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowRepository workflowRepository;
    private final TaskRepository taskRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Clear old data for the demo
        workflowInstanceRepository.deleteAll();
        taskRepository.deleteAll();

        log.info("🚀 Initiating Massive Data Injection...");
        
        // 1. Users
        User admin = upsertUser("admin@bpflow.com", "Admin1234", "Admin", "System", "ADMIN");
        User manager = upsertUser("manager@bpflow.com", "Admin1234", "Carlos", "Gerente", "MANAGER");
        User officer = upsertUser("officer@bpflow.com", "Admin1234", "Elena", "Oficial", "OFFICER");
        User client = upsertUser("client@example.com", "User1234", "Juan", "Pérez", "CLIENT");

        // 2. Instances & Tasks
        initializeMassiveData(admin, manager, officer, client);
        
        log.info("✅ Data Injection Complete. System is now 'alive'.");
    }

    private void initializeMassiveData(User admin, User manager, User officer, User client) {
        if (workflowInstanceRepository.count() > 5) {
            log.info("Data already exists, skipping massive injection.");
            return;
        }

        List<Workflow> workflows = workflowRepository.findAll();
        if (workflows.isEmpty()) {
            log.warn("No workflows found. Seed workflows first.");
            return;
        }

        Workflow mainWf = workflows.get(0);

        // CREATE 10 INSTANCES
        for (int i = 1; i <= 10; i++) {
            boolean isCompleted = i > 7;
            WorkflowInstance instance = WorkflowInstance.builder()
                    .workflowId(mainWf.getId())
                    .workflowName(mainWf.getName())
                    .referenceNumber("PROC-2024-" + String.format("%03d", i))
                    .initiatedBy(client.getId())
                    .clientId(client.getId())
                    .clientName(client.getFirstName() + " " + client.getLastName())
                    .clientEmail(client.getEmail())
                    .status(isCompleted ? WorkflowInstance.InstanceStatus.COMPLETED : WorkflowInstance.InstanceStatus.RUNNING)
                    .priority(i % 3 == 0 ? WorkflowInstance.Priority.HIGH : WorkflowInstance.Priority.NORMAL)
                    .currentNodeName(isCompleted ? "Finalizado" : "Revisión Manual")
                    .createdAt(LocalDateTime.now().minusDays(i))
                    .history(new ArrayList<>())
                    .build();
            
            workflowInstanceRepository.save(instance);

            // Create Tasks for Running Instances
            if (!isCompleted) {
                createTaskForInstance(instance, i % 2 == 0 ? manager : officer, i);
            }

            // Create Audit Logs
            createAuditLog(instance, "INSTANCE_STARTED", admin, "Process initiated automatically");
        }

        // 3. Fake Notifications
        createNotification(admin.getId(), "System Health", "All microservices are operational", Notification.NotificationType.SYSTEM_MESSAGE);
        createNotification(manager.getId(), "New Task", "Expense report #442 requires your approval", Notification.NotificationType.TASK_ASSIGNED);
        createNotification(manager.getId(), "SLA Warning", "Process PROC-2024-002 is near deadline", Notification.NotificationType.SLA_WARNING);
        createNotification(officer.getId(), "Update", "Client documents for PROC-2024-005 updated", Notification.NotificationType.SYSTEM_MESSAGE);
    }

    private void createTaskForInstance(WorkflowInstance instance, User user, int i) {
        List<Task.Attachment> attachments = List.of(
            Task.Attachment.builder()
                .fileName("Documento_Identidad.pdf")
                .fileType("application/pdf")
                .fileUrl("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
                .fileSize(102400)
                .uploadedBy("Sistema")
                .uploadedAt(LocalDateTime.now().minusDays(1))
                .build(),
            Task.Attachment.builder()
                .fileName("Formulario_Solicitud.jpg")
                .fileType("image/jpeg")
                .fileUrl("https://placehold.co/600x400/0b1628/white?text=BPFlow+Doc+Preview")
                .fileSize(204800)
                .uploadedBy("Sistema")
                .uploadedAt(LocalDateTime.now().minusDays(1))
                .build()
        );

        Task task = Task.builder()
                .instanceId(instance.getId())
                .workflowId(instance.getWorkflowId())
                .workflowName(instance.getWorkflowName())
                .title("Revisión de Documentación " + instance.getReferenceNumber())
                .description("Por favor, valide que los archivos adjuntos cumplan con la normativa vigente.")
                .assignedTo(user.getId())
                .assignedRole(user.getRoles().iterator().next())
                .status(Task.TaskStatus.NEW)
                .priority(i % 3 == 0 ? Task.Priority.HIGH : Task.Priority.NORMAL)
                .attachments(attachments)
                .createdAt(LocalDateTime.now().minusHours(i * 2))
                .dueAt(LocalDateTime.now().plusDays(2))
                .build();
        taskRepository.save(task);
    }

    private void createAuditLog(WorkflowInstance instance, String action, User user, String detail) {
        AuditLog logEntry = AuditLog.builder()
                .resourceId(instance.getId())
                .resourceType("INSTANCE")
                .action(action)
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userRole(user.getRoles().iterator().next())
                .detail(detail)
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
        auditLogRepository.save(logEntry);
    }

    private void createNotification(String userId, String title, String msg, Notification.NotificationType type) {
        Notification n = Notification.builder()
                .recipientId(userId)
                .title(title)
                .message(msg)
                .type(type)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(n);
    }

    private User upsertUser(String email, String password, String firstName, String lastName, String role) {
        // ← FIX: if the user already exists, NEVER overwrite their password or data
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setRoles(Set.of(role));
            user.setEnabled(true);
            user.setAccountNonLocked(true);
            log.info("🌱 Seeding default user: {}", email);
            return userRepository.save(user);
        });
    }
}
