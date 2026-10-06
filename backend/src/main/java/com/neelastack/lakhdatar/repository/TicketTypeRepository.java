package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Enums; import com.neelastack.lakhdatar.domain.TicketType; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface TicketTypeRepository extends JpaRepository<TicketType,Long>{
 Optional<TicketType> findByPublicId(UUID id);
 List<TicketType> findByEventIdOrderByPriceMinorUnitsAsc(Long eventId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TicketType t where t.eventId=:eventId order by t.id asc") List<TicketType> findByEventIdForUpdateOrderByIdAsc(@Param("eventId") Long eventId);
 @Query("select t.eventId, coalesce(sum(t.soldQuantity),0) from TicketType t where t.eventId in :eventIds group by t.eventId") List<Object[]> soldByEventIds(@Param("eventIds") Collection<Long> eventIds);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TicketType t where t.id=:id") Optional<TicketType> findByIdForUpdate(Long id);
 /** Per-event aggregate for catalogue cards: [eventId, minOnSalePrice, availableQuantity, totalQuantity, onSaleTypeCount, onSaleAvailableQuantity]. */
 @Query("select t.eventId, min(case when (t.saleStartsAt is null or t.saleStartsAt <= :now) and (t.saleEndsAt is null or t.saleEndsAt > :now) then t.priceMinorUnits else null end), sum(t.totalQuantity - t.reservedQuantity - t.soldQuantity), sum(t.totalQuantity), sum(case when (t.saleStartsAt is null or t.saleStartsAt <= :now) and (t.saleEndsAt is null or t.saleEndsAt > :now) then 1 else 0 end), sum(case when (t.saleStartsAt is null or t.saleStartsAt <= :now) and (t.saleEndsAt is null or t.saleEndsAt > :now) then (t.totalQuantity - t.reservedQuantity - t.soldQuantity) else 0 end) from TicketType t where t.eventId in :eventIds and t.status = :status group by t.eventId")
 List<Object[]> aggregateForEvents(@org.springframework.data.repository.query.Param("eventIds") java.util.Collection<Long> eventIds, @org.springframework.data.repository.query.Param("status") Enums.TicketTypeStatus status, @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
