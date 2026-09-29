package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.TicketCheckin; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface TicketCheckinRepository extends JpaRepository<TicketCheckin,Long>{
 @Query(value="select count(*) from ticket_checkins c where c.event_id=:eventId and c.result='ACCEPTED'",nativeQuery=true) long countAcceptedForEvent(Long eventId);
 @Query(value="select count(*) from ticket_checkins c where c.event_id=:eventId and c.result<>'ACCEPTED'",nativeQuery=true) long countRejectedForEvent(Long eventId);
 @Query(value="select c.* from ticket_checkins c where c.event_id=:eventId order by c.created_at desc limit :limit",nativeQuery=true) List<TicketCheckin> recentForEvent(Long eventId,int limit);
}
