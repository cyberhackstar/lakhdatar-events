package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.TicketReservation; import com.neelastack.lakhdatar.domain.Enums; import org.springframework.data.jpa.repository.*; import java.time.Instant; import java.util.*;
public interface TicketReservationRepository extends JpaRepository<TicketReservation,Long>{ @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @org.springframework.data.jpa.repository.Query("select r from TicketReservation r where r.id=:id") Optional<TicketReservation> findByIdForUpdate(Long id); @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @org.springframework.data.jpa.repository.Query("select r from TicketReservation r where r.orderId=:orderId order by r.id asc") List<TicketReservation> findByOrderIdForUpdate(Long orderId); List<TicketReservation> findByOrderIdOrderByIdAsc(Long orderId); List<TicketReservation> findTop200ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(Enums.ReservationStatus status,Instant cutoff); 
 @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true, flushAutomatically=true)
 @org.springframework.data.jpa.repository.Query(value="""
     WITH released AS (
       UPDATE ticket_reservations
          SET status='RELEASED'
        WHERE event_id=:eventId AND status='HELD'
        RETURNING ticket_type_id, quantity
     ), totals AS (
       SELECT ticket_type_id, SUM(quantity) AS quantity
         FROM released GROUP BY ticket_type_id
     )
     UPDATE ticket_types tt
        SET reserved_quantity = reserved_quantity - totals.quantity
       FROM totals
      WHERE tt.id = totals.ticket_type_id
     """, nativeQuery=true)
 int releaseHeldForEventAndAdjustInventory(@org.springframework.data.repository.query.Param("eventId") Long eventId);
}
