package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.EventManagerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventManagerAssignmentRepository extends JpaRepository<EventManagerAssignment, Long> {
    boolean existsByEventIdAndUserId(Long eventId, Long userId);
    Optional<EventManagerAssignment> findByEventIdAndUserId(Long eventId, Long userId);
    List<EventManagerAssignment> findByUserIdOrderByEventIdDesc(Long userId);
    List<EventManagerAssignment> findByEventIdOrderByUserIdAsc(Long eventId);
    void deleteByEventIdAndUserId(Long eventId, Long userId);
}
