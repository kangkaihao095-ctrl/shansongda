package com.shansuda.account.repo;

import com.shansuda.account.domain.MerchantPromo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantPromoRepo extends JpaRepository<MerchantPromo, Long> {
    Optional<MerchantPromo> findFirstByMerchantIdAndStatus(Long merchantId, String status);
}
