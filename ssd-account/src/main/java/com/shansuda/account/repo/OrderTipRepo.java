package com.shansuda.account.repo;

import com.shansuda.account.domain.OrderTip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OrderTipRepo extends JpaRepository<OrderTip, Long> {
    boolean existsByOrderId(Long orderId);

    List<OrderTip> findByRiderIdAndCreatedAtGreaterThanEqual(Long riderId, Instant from);
}
