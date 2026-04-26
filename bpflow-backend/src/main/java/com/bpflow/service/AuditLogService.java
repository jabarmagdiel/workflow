package com.bpflow.service;

import com.bpflow.model.AuditLog;
import com.bpflow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repo;

    /** Core log method - call this from controllers */
    public void log(String userId, String userEmail, String userRole,
                    String action, String resourceType, String resourceId,
                    boolean success, String detail, HttpServletRequest request) {

        String ip = resolveIp(request);
        String ua = request != null ? request.getHeader("User-Agent") : null;

        AuditLog entry = AuditLog.builder()
                .userId(userId)
                .userEmail(userEmail)
                .userRole(userRole)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(success)
                .detail(detail)
                .ipAddress(ip)
                .userAgent(ua)
                .timestamp(LocalDateTime.now())
                .build();

        repo.save(entry);
    }

    /** Convenience - no request context (called from services) */
    public void log(String userId, String userEmail, String userRole,
                    String action, String resourceType, String resourceId,
                    boolean success, String detail) {
        log(userId, userEmail, userRole, action, resourceType, resourceId, success, detail, null);
    }

    private String resolveIp(HttpServletRequest req) {
        if (req == null) return "server";
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isEmpty())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}
