package com.shansuda.activity.repo;

import com.shansuda.activity.domain.ActivitySku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivitySkuRepo extends JpaRepository<ActivitySku, Long> {
    List<ActivitySku> findByActivityId(Long activityId);
    Optional<ActivitySku> findByActivityIdAndSkuId(Long activityId, Long skuId);
}
