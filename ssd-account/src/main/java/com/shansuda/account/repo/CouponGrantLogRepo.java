package com.shansuda.account.repo;

import com.shansuda.account.domain.CouponGrantLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponGrantLogRepo extends JpaRepository<CouponGrantLog, String> {
}
