package com.bpflow.repository;

import com.bpflow.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    Page<AuditLog> findByUserId(String userId, Pageable pageable);

    Page<AuditLog> findByTimestampBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    List<AuditLog> findByUserIdAndTimestampBetween(String userId, LocalDateTime from, LocalDateTime to);

    List<AuditLog> findByResourceTypeAndResourceId(String type, String id);
}
