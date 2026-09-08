package com.shansuda.account.repo;

import com.shansuda.account.domain.MerchantReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantReviewRepo extends JpaRepository<MerchantReview, Long> {
    boolean existsByOrderId(Long orderId);

    Optional<MerchantReview> findByOrderId(Long orderId);

    Page<MerchantReview> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    Page<MerchantReview> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByMerchantId(Long merchantId);
}
