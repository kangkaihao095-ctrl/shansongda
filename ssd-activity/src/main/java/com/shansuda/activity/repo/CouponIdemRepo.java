package com.shansuda.activity.repo;

import com.shansuda.activity.domain.CouponIdem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIdemRepo extends JpaRepository<CouponIdem, String> {
}
