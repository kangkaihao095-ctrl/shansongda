package com.shansuda.account.repo;

import com.shansuda.account.domain.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepo extends JpaRepository<UserCoupon, Long> {
    List<UserCoupon> findByUserId(Long userId);
    List<UserCoupon> findByUserIdAndStatus(Long userId, String status);
    Optional<UserCoupon> findByCouponIdAndUserId(Long couponId, Long userId);
    Optional<UserCoupon> findByCouponIdAndUserIdAndStatus(Long couponId, Long userId, String status);
    long countByUserIdAndStatus(Long userId, String status);
}
