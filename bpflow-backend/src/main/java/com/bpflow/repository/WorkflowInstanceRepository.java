package com.bpflow.repository;

import com.bpflow.model.WorkflowInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowInstanceRepository extends MongoRepository<WorkflowInstance, String> {
    List<WorkflowInstance> findByStatus(WorkflowInstance.InstanceStatus status);

    List<WorkflowInstance> findByInitiatedBy(String userId);

    List<WorkflowInstance> findByClientId(String clientId);

    List<WorkflowInstance> findByWorkflowId(String workflowId);

    Page<WorkflowInstance> findByStatus(WorkflowInstance.InstanceStatus status, Pageable pageable);

    List<WorkflowInstance> findByStatusIn(List<WorkflowInstance.InstanceStatus> statuses);

    long countByWorkflowIdAndStatus(String workflowId, WorkflowInstance.InstanceStatus status);

    List<WorkflowInstance> findByFlaggedForReview(boolean flagged);
}
