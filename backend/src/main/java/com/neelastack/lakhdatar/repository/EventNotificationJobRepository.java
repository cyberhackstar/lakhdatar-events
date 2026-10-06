package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.EventNotificationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventNotificationJobRepository extends JpaRepository<EventNotificationJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from EventNotificationJob j where j.id=:id")
    Optional<EventNotificationJob> findByIdForUpdate(@Param("id") Long id);

    List<EventNotificationJob> findTop500ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            List<EventNotificationJob.Status> statuses, Instant now);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into event_notification_jobs
                (event_id, order_id, kind, change_key, old_starts_at, old_ends_at,
                 status, attempts, next_attempt_at, created_at, updated_at)
            select :eventId, o.id, :kind, :changeKey, :oldStartsAt, :oldEndsAt,
                   'PENDING', 0, now(), now(), now()
              from orders o
             where o.event_id = :eventId
               and o.customer_email is not null
               and btrim(o.customer_email) <> ''
               and o.status in ('CONFIRMED', 'CANCELLED')
            on conflict (event_id, order_id, kind, change_key) do nothing
            """, nativeQuery = true)
    int enqueueForEvent(@Param("eventId") Long eventId,
                        @Param("kind") String kind,
                        @Param("changeKey") String changeKey,
                        @Param("oldStartsAt") Instant oldStartsAt,
                        @Param("oldEndsAt") Instant oldEndsAt);

    @Modifying
    @Query("delete from EventNotificationJob j where j.status in :statuses and j.updatedAt < :cutoff")
    long deleteHistory(@Param("statuses") List<EventNotificationJob.Status> statuses, @Param("cutoff") Instant cutoff);
}
