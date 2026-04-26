package com.bpflow.repository;

import com.bpflow.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByRecipientIdAndReadFalse(String userId);

    long countByRecipientIdAndReadFalse(String userId);
}
