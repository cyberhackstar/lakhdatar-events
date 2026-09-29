package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.domain.Enums;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment,Long>{
 Optional<Payment> findByOrderId(Long orderId);
 Optional<Payment> findByPublicId(UUID id);
 Optional<Payment> findByRazorpayOrderId(String id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Payment p where p.id=:id") Optional<Payment> findByIdForUpdate(Long id);
 List<Payment> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(Collection<Enums.PaymentStatus> statuses, Instant cutoff);
 List<Payment> findTop100ByRazorpayOrderIdIsNullAndStatusInOrderByCreatedAtAsc(Collection<Enums.PaymentStatus> statuses);
 @Query("select coalesce(sum(p.amountMinor),0) from Payment p, Order o where o.id=p.orderId and o.eventId=:eventId and p.status in :statuses") long sumSuccessfulByEventId(Long eventId,java.util.Collection<Enums.PaymentStatus> statuses);
 @Query("select p from Payment p, Order o, Event e where o.id=p.orderId and e.id=o.eventId and e.status=:eventStatus and p.status in :statuses and not exists (select r.id from Refund r where r.paymentId=p.id) order by p.createdAt asc") List<Payment> findByCancelledEventAndStatusWithoutRefund(@org.springframework.data.repository.query.Param("eventStatus") Enums.EventStatus eventStatus, @org.springframework.data.repository.query.Param("statuses") java.util.Collection<Enums.PaymentStatus> statuses, org.springframework.data.domain.Pageable pageable);
}
