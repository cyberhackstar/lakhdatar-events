package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.OrderItem; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface OrderItemRepository extends JpaRepository<OrderItem,Long>{ List<OrderItem> findByOrderId(Long orderId); Optional<OrderItem> findByOrderIdAndTicketTypeId(Long orderId,Long ticketTypeId); }
