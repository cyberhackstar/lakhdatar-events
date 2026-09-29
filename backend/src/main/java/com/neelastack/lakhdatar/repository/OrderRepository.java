package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Order; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface OrderRepository extends JpaRepository<Order,Long>{ Optional<Order> findByPublicId(UUID id); Optional<Order> findByOrderNumber(String number); Optional<Order> findByIdempotencyKey(String key); @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from Order o where o.id=:id") Optional<Order> findByIdForUpdate(Long id); }
