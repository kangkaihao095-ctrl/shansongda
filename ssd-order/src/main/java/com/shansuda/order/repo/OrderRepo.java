package com.shansuda.order.repo;

import com.shansuda.order.domain.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepo extends JpaRepository<DeliveryOrder, Long> {
    Optional<DeliveryOrder> findByIdempotencyKey(String idempotencyKey);

    List<DeliveryOrder> findByIdIn(Collection<Long> ids);
}
