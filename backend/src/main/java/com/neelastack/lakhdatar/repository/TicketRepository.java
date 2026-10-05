package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Ticket; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface TicketRepository extends JpaRepository<Ticket,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Ticket t where t.publicId=:id") Optional<Ticket> findByPublicIdForUpdate(UUID id);
 Optional<Ticket> findByPublicId(UUID id); Optional<Ticket> findByTicketNumber(String number); List<Ticket> findByOrderIdOrderByTicketNumberAsc(Long orderId); List<Ticket> findByEventIdOrderByTicketNumberAsc(Long eventId);
 long countByEventId(Long eventId); long countByEventIdAndStatus(Long eventId, com.neelastack.lakhdatar.domain.Enums.TicketStatus status);
 @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true, flushAutomatically=true)
 @org.springframework.data.jpa.repository.Query(value="update tickets set status='CANCELLED' where event_id=:eventId and status='ISSUED'", nativeQuery=true)
 int cancelIssuedForEvent(@Param("eventId") Long eventId);
 @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true, flushAutomatically=true)
 @org.springframework.data.jpa.repository.Query(value="update tickets set status='CANCELLED' where order_id=:orderId and status<>'CHECKED_IN'", nativeQuery=true)
 int cancelNonCheckedInForOrder(@Param("orderId") Long orderId);
 @org.springframework.data.jpa.repository.Modifying(clearAutomatically=false, flushAutomatically=true)
 @org.springframework.data.jpa.repository.Query(value="""
      update tickets t
         set status='CHECKED_IN', checked_in_at=:checkedInAt
       where t.id=:ticketId
         and t.event_id=:eventId
         and t.status='ISSUED'
         and exists (select 1 from events e where e.id=t.event_id and e.status='PUBLISHED')
      """, nativeQuery=true)
 int markCheckedInIfEventPublished(@Param("ticketId") Long ticketId, @Param("eventId") Long eventId, @Param("checkedInAt") java.time.Instant checkedInAt);
 @Query("select t.eventId, count(t) from Ticket t where t.eventId in :eventIds and t.status=:status group by t.eventId") List<Object[]> countByEventIdsAndStatus(@Param("eventIds") Collection<Long> eventIds, @Param("status") com.neelastack.lakhdatar.domain.Enums.TicketStatus status);
}
