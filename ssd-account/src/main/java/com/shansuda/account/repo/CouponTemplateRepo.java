package com.shansuda.account.repo;

import com.shansuda.account.domain.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponTemplateRepo extends JpaRepository<CouponTemplate, String> {
    List<CouponTemplate> findByScene(String scene);
}
