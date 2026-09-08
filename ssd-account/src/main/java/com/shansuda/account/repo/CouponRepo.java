package com.shansuda.account.repo;

import com.shansuda.account.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepo extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findFirstByActivityId(Long activityId);

    Optional<Coupon> findFirstByCode(String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Coupon c set c.stock = c.stock - 1 where c.id = :id and c.stock > 0")
    int deductStock(@Param("id") long id);
}

