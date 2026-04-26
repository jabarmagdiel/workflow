package com.bpflow.repository;

import com.bpflow.model.FraudAlert;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudAlertRepository extends MongoRepository<FraudAlert, String> {
    List<FraudAlert> findByStatus(FraudAlert.AlertStatus status);

    List<FraudAlert> findByUserId(String userId);

    List<FraudAlert> findByInstanceId(String instanceId);

    List<FraudAlert> findBySeverityAndStatus(FraudAlert.AlertSeverity severity, FraudAlert.AlertStatus status);

    long countByStatus(FraudAlert.AlertStatus status);
}
