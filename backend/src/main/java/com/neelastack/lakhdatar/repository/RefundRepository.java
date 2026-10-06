package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findAllByPaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);
    Optional<Refund> findFirstByPaymentIdOrderByCreatedAtDescIdDesc(Long paymentId);
    Optional<Refund> findByPublicId(UUID id);
    Optional<Refund> findByRazorpayRefundId(String id);
    Optional<Refund> findByProviderRefundId(String id);
    Optional<Refund> findByPaymentIdAndProviderRefundId(Long paymentId, String providerRefundId);
    List<Refund> findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(Collection<Enums.RefundStatus> statuses, Instant now, org.springframework.data.domain.Pageable pageable);
}
