package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "coupon")
public class Coupon {

    @Id
    private Long id;
    private String name;
    @Column(name = "merchant_id")
    private Long merchantId;
    @Column(name = "activity_id")
    private Long activityId;
    private String type;
    @Column(name = "min_spend_cents")
    private Integer minSpendCents;
    @Column(name = "discount_cents")
    private Integer discountCents;
    @Column(name = "percent_off")
    private Integer percentOff;
    private Integer stock;
    @Column(name = "start_at")
    private Instant startAt;
    @Column(name = "end_at")
    private Instant endAt;
    private String status;
    private String code;
    @Column(name = "member_only")
    private Boolean memberOnly;
    private String icon;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getMinSpendCents() { return minSpendCents; }
    public void setMinSpendCents(Integer minSpendCents) { this.minSpendCents = minSpendCents; }
    public Integer getDiscountCents() { return discountCents; }
    public void setDiscountCents(Integer discountCents) { this.discountCents = discountCents; }
    public Integer getPercentOff() { return percentOff; }
    public void setPercentOff(Integer percentOff) { this.percentOff = percentOff; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Boolean getMemberOnly() { return memberOnly; }
    public void setMemberOnly(Boolean memberOnly) { this.memberOnly = memberOnly; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
