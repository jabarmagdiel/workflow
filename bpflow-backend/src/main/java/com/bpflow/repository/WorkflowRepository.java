package com.bpflow.repository;

import com.bpflow.model.Workflow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends MongoRepository<Workflow, String> {
    List<Workflow> findByStatus(Workflow.WorkflowStatus status);

    List<Workflow> findByCreatedBy(String userId);

    Optional<Workflow> findByIdAndStatus(String id, Workflow.WorkflowStatus status);

    List<Workflow> findByStatusIn(List<Workflow.WorkflowStatus> statuses);

    boolean existsByNameAndStatus(String name, Workflow.WorkflowStatus status);

    boolean existsByName(String name);
}
