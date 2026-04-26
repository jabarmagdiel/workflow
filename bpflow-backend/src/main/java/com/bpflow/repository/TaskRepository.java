package com.bpflow.repository;

import com.bpflow.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByAssignedTo(String userId);

    List<Task> findByAssignedToAndStatus(String userId, Task.TaskStatus status);

    Page<Task> findByAssignedTo(String userId, Pageable pageable);

    List<Task> findByInstanceId(String instanceId);

    List<Task> findByAssignedRole(String role);

    List<Task> findByStatus(Task.TaskStatus status);

    List<Task> findByDueAtBeforeAndStatusNot(LocalDateTime now, Task.TaskStatus status);

    long countByAssignedToAndStatus(String userId, Task.TaskStatus status);

    long countByStatus(Task.TaskStatus status);
}
