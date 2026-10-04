package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.TicketMailJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface TicketMailJobRepository extends JpaRepository<TicketMailJob, Long> {
    Optional<TicketMailJob> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from TicketMailJob j where j.id=:id")
    Optional<TicketMailJob> findByIdForUpdate(@Param("id") Long id);

    List<TicketMailJob> findTop100ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            Collection<TicketMailJob.Status> statuses, Instant now);

    long countByStatusIn(Collection<TicketMailJob.Status> statuses);

    long deleteByStatusInAndUpdatedAtBefore(Collection<TicketMailJob.Status> statuses, Instant cutoff);
}
