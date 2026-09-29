package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.EventStaff; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface EventStaffRepository extends JpaRepository<EventStaff,Long>{ boolean existsByEventIdAndUserId(Long eventId,Long userId); Optional<EventStaff> findByEventIdAndUserId(Long eventId,Long userId); List<EventStaff> findByUserIdOrderByEventIdDesc(Long userId); }
