package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchant_promo")
public class MerchantPromo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "merchant_id")
    private Long merchantId;
    @Column(name = "min_spend_cents")
    private Integer minSpendCents;
    @Column(name = "off_cents")
    private Integer offCents;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public Integer getMinSpendCents() { return minSpendCents; }
    public void setMinSpendCents(Integer minSpendCents) { this.minSpendCents = minSpendCents; }
    public Integer getOffCents() { return offCents; }
    public void setOffCents(Integer offCents) { this.offCents = offCents; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
