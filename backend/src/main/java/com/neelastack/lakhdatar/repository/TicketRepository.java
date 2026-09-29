package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Ticket; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface TicketRepository extends JpaRepository<Ticket,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Ticket t where t.publicId=:id") Optional<Ticket> findByPublicIdForUpdate(UUID id);
 Optional<Ticket> findByPublicId(UUID id); Optional<Ticket> findByTicketNumber(String number); List<Ticket> findByOrderIdOrderByTicketNumberAsc(Long orderId); List<Ticket> findByEventIdOrderByTicketNumberAsc(Long eventId);
 long countByEventId(Long eventId); long countByEventIdAndStatus(Long eventId, com.neelastack.lakhdatar.domain.Enums.TicketStatus status);
}
