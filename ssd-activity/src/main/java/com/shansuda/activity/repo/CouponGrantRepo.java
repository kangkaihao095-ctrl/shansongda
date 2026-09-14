package com.shansuda.activity.repo;

import com.shansuda.activity.domain.CouponGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponGrantRepo extends JpaRepository<CouponGrant, Long> {
    boolean existsByActivityIdAndUserIdAndScene(Long activityId, Long userId, String scene);

    Optional<CouponGrant> findByActivityIdAndUserIdAndScene(Long activityId, Long userId, String scene);
}
