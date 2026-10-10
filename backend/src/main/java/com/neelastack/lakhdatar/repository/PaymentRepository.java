package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.domain.Enums;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment,Long>{
 Optional<Payment> findByOrderId(Long orderId);
 @Query("select o.eventId, coalesce(sum(p.amountMinor),0) from Payment p, Order o where p.orderId=o.id and o.eventId in :eventIds and p.status in :statuses group by o.eventId") List<Object[]> successfulRevenueByEventIds(@Param("eventIds") Collection<Long> eventIds, @Param("statuses") Collection<com.neelastack.lakhdatar.domain.Enums.PaymentStatus> statuses);
 Optional<Payment> findByPublicId(UUID id);
 Optional<Payment> findByRazorpayOrderId(String id);
 Optional<Payment> findByProviderOrderId(String id);
 Optional<Payment> findByProviderPaymentId(String id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Payment p where p.id=:id") Optional<Payment> findByIdForUpdate(Long id);
 List<Payment> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(Collection<Enums.PaymentStatus> statuses, Instant cutoff);
 @Query(value="select p from Payment p where p.providerOrderId is not null and p.status in :statuses and p.createdAt < :cutoff and p.createdAt > :windowStart and (p.lastReconciledAt is null or p.lastReconciledAt < :recheckBefore) order by p.lastReconciledAt asc nulls first, p.createdAt asc")
 List<Payment> findReconciliationCandidates(@Param("statuses") Collection<Enums.PaymentStatus> statuses, @Param("cutoff") Instant cutoff, @Param("windowStart") Instant windowStart, @Param("recheckBefore") Instant recheckBefore, org.springframework.data.domain.Pageable pageable);
 @Query("select p from Payment p, Order o " +
        "where o.id = p.orderId " +
        "and p.providerOrderId is null " +
        "and p.status in :statuses " +
        "and o.status in :orderStatuses " +
        "and p.createdAt > :windowStart " +
        "and (p.lastReconciledAt is null or p.lastReconciledAt < :recheckBefore) " +
        "order by p.lastReconciledAt asc nulls first, p.createdAt asc")
 List<Payment> findMissingProviderOrderCandidates(
         @Param("statuses") Collection<Enums.PaymentStatus> statuses,
         @Param("orderStatuses") Collection<Enums.OrderStatus> orderStatuses,
         @Param("windowStart") Instant windowStart,
         @Param("recheckBefore") Instant recheckBefore,
         org.springframework.data.domain.Pageable pageable);
 @org.springframework.transaction.annotation.Transactional
 @Modifying(clearAutomatically=false, flushAutomatically=false)
 @Query("update Payment p set p.lastReconciledAt=:now where p.id=:id")
 int markReconciled(@Param("id") Long id, @Param("now") Instant now);
 List<Payment> findTop100ByRazorpayOrderIdIsNullAndStatusInOrderByCreatedAtAsc(Collection<Enums.PaymentStatus> statuses);
 List<Payment> findTop100ByProviderOrderIdIsNullAndStatusInOrderByCreatedAtAsc(Collection<Enums.PaymentStatus> statuses);
 @Query("select coalesce(sum(p.amountMinor),0) from Payment p, Order o where o.id=p.orderId and o.eventId=:eventId and p.status in :statuses") long sumSuccessfulByEventId(Long eventId,java.util.Collection<Enums.PaymentStatus> statuses);
 @Query(value="""
     select p.*
       from payments p
       join orders o on o.id = p.order_id
       join events e on e.id = o.event_id
      where e.status = :eventStatus
        and p.status in (:statuses)
        and p.amount_minor > coalesce((
              select sum(r.amount_minor)
                from refunds r
               where r.payment_id = p.id
                 and r.status = 'COMPLETED'
            ), 0)
        and not exists (
              select 1 from refunds r
               where r.payment_id = p.id
                 and r.status = 'PROCESSING'
            )
        and not exists (
              select 1 from refunds r
               where r.payment_id = p.id
                 and r.status = 'FAILED'
                 and r.reason = 'Event cancellation'
                 and not r.manual_review_required
                 and coalesce(r.next_attempt_at, now()) > now()
            )
        and coalesce((
              select sum(greatest(r.attempt_count, 1))
                from refunds r
               where r.payment_id = p.id
                 and r.reason = 'Event cancellation'
                 and not r.manual_review_required
            ), 0) < :maxAttempts
      order by p.created_at asc
     """, nativeQuery=true)
 List<Payment> findCancelledEventRefundCandidates(@org.springframework.data.repository.query.Param("eventStatus") String eventStatus,
                                                  @org.springframework.data.repository.query.Param("statuses") java.util.Collection<String> statuses,
                                                  @org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts,
                                                  org.springframework.data.domain.Pageable pageable);
}
