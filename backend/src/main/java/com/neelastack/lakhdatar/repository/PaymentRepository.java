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
 @Query(value="select p from Payment p where p.providerOrderId is null and p.status in :statuses and p.createdAt > :windowStart and (p.lastReconciledAt is null or p.lastReconciledAt < :recheckBefore) order by p.lastReconciledAt asc nulls first, p.createdAt asc")
 List<Payment> findMissingProviderOrderCandidates(@Param("statuses") Collection<Enums.PaymentStatus> statuses, @Param("windowStart") Instant windowStart, @Param("recheckBefore") Instant recheckBefore, org.springframework.data.domain.Pageable pageable);
 @org.springframework.transaction.annotation.Transactional
 @Modifying(clearAutomatically=false, flushAutomatically=false)
 @Query("update Payment p set p.lastReconciledAt=:now where p.id=:id")
 int markReconciled(@Param("id") Long id, @Param("now") Instant now);
 List<Payment> findTop100ByRazorpayOrderIdIsNullAndStatusInOrderByCreatedAtAsc(Collection<Enums.PaymentStatus> statuses);
 List<Payment> findTop100ByProviderOrderIdIsNullAndStatusInOrderByCreatedAtAsc(Collection<Enums.PaymentStatus> statuses);
 @Query("select coalesce(sum(p.amountMinor),0) from Payment p, Order o where o.id=p.orderId and o.eventId=:eventId and p.status in :statuses") long sumSuccessfulByEventId(Long eventId,java.util.Collection<Enums.PaymentStatus> statuses);
 @Query("select p from Payment p, Order o, Event e where o.id=p.orderId and e.id=o.eventId and e.status=:eventStatus and p.status in :statuses and not exists (select r.id from Refund r where r.paymentId=p.id) order by p.createdAt asc") List<Payment> findByCancelledEventAndStatusWithoutRefund(@org.springframework.data.repository.query.Param("eventStatus") Enums.EventStatus eventStatus, @org.springframework.data.repository.query.Param("statuses") java.util.Collection<Enums.PaymentStatus> statuses, org.springframework.data.domain.Pageable pageable);
}
