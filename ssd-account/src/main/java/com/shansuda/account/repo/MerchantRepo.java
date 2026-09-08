package com.shansuda.account.repo;

import com.shansuda.account.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepo extends JpaRepository<Merchant, Long> {
    java.util.Optional<Merchant> findByShopName(String shopName);
}
