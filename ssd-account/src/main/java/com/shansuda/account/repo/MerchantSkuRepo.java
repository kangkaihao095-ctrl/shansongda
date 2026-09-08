package com.shansuda.account.repo;

import com.shansuda.account.domain.MerchantSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MerchantSkuRepo extends JpaRepository<MerchantSku, Long> {
    List<MerchantSku> findByMerchantId(Long merchantId);

    long countByMerchantId(Long merchantId);

    java.util.Optional<MerchantSku> findByMerchantIdAndId(Long merchantId, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MerchantSku s set s.stock = s.stock - :qty where s.id = :id and s.stock >= :qty")
    int deductStock(@Param("id") long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MerchantSku s set s.stock = s.stock + :qty where s.id = :id")
    int restoreStock(@Param("id") long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MerchantSku s set s.monthSales = coalesce(s.monthSales, 0) + :qty where s.id = :id")
    int bumpMonthSales(@Param("id") long id, @Param("qty") int qty);
}
