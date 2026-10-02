package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RefundRepository extends JpaRepository<Refund,Long>{
 Optional<Refund> findByPaymentId(Long paymentId);
 Optional<Refund> findByPublicId(UUID id);
 Optional<Refund> findByRazorpayRefundId(String id);
 Optional<Refund> findByProviderRefundId(String id);
 List<Refund> findTop100ByStatusInOrderByCreatedAtAsc(Collection<Enums.RefundStatus> statuses);
}
