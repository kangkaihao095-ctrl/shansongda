package com.shansuda.account.repo;

import com.shansuda.account.domain.OrderTip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTipRepo extends JpaRepository<OrderTip, Long> {
    boolean existsByOrderId(Long orderId);
}
