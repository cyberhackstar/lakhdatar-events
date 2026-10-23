package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Order; import com.neelastack.lakhdatar.domain.Enums; import jakarta.persistence.LockModeType; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.time.Instant; import java.util.*;
public interface OrderRepository extends JpaRepository<Order,Long>{
 Optional<Order> findByPublicId(UUID id);
 Optional<Order> findByOrderNumber(String number);
 Optional<Order> findByIdempotencyKey(String key);
 @Query("select distinct o from Order o, Payment p where p.orderId=o.id and o.status=:status and p.status=:paymentStatus and o.createdAt>:after and not exists (select j.id from TicketMailJob j where j.orderId=o.id) order by o.createdAt asc")
 List<Order> findConfirmedPaidWithoutMailJob(@Param("status") Enums.OrderStatus status, @Param("paymentStatus") Enums.PaymentStatus paymentStatus, @Param("after") Instant after, Pageable pageable);
 @Query("select o from Order o where o.status in :openStatuses and o.createdAt < :createdBefore and exists (select r.id from TicketReservation r where r.orderId=o.id) and not exists (select live.id from TicketReservation live where live.orderId=o.id and live.status=:heldStatus and live.expiresAt > :now) order by o.updatedAt asc, o.id asc")
 List<Order> findOpenOrdersWithoutLiveReservations(@Param("openStatuses") Collection<Enums.OrderStatus> openStatuses, @Param("createdBefore") Instant createdBefore, @Param("heldStatus") Enums.ReservationStatus heldStatus, @Param("now") Instant now, Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from Order o where o.id=:id") Optional<Order> findByIdForUpdate(Long id);
}
