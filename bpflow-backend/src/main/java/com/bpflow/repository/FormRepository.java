package com.bpflow.repository;

import com.bpflow.model.Form;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormRepository extends MongoRepository<Form, String> {
    List<Form> findByWorkflowId(String workflowId);

    Optional<Form> findByWorkflowIdAndNodeId(String workflowId, String nodeId);

    List<Form> findByCreatedBy(String userId);
}
