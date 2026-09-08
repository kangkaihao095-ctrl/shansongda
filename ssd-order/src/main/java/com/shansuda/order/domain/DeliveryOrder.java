package com.shansuda.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "t_order")
public class DeliveryOrder {

    @Id
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "merchant_id")
    private Long merchantId;
    @Column(name = "rider_id")
    private Long riderId;
    private String status;
    @Column(name = "goods_amount_cents")
    private Integer goodsAmountCents;
    @Column(name = "freight_cents")
    private Integer freightCents;
    @Column(name = "freight_strategy")
    private String freightStrategy;
    @Column(name = "penalty_cents")
    private Integer penaltyCents;
    @Column(name = "user_lat")
    private Double userLat;
    @Column(name = "user_lon")
    private Double userLon;
    @Column(name = "merchant_lat")
    private Double merchantLat;
    @Column(name = "merchant_lon")
    private Double merchantLon;
    @Column(name = "address_detail")
    private String addressDetail;
    @Column(name = "activity_id")
    private Long activityId;
    @Column(name = "sku_snapshot")
    private String skuSnapshot;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    private Long version;
    @Column(name = "pay_channel")
    private String payChannel;
    @Column(name = "pay_status")
    private String payStatus;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "pay_amount_cents")
    private Integer payAmountCents;
    @Column(name = "cancel_reason")
    private String cancelReason;
    @Column(name = "refund_reason")
    private String refundReason;
    @Column(name = "refund_reject_reason")
    private String refundRejectReason;
    @Column(name = "resume_status")
    private String resumeStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public Long getRiderId() { return riderId; }
    public void setRiderId(Long riderId) { this.riderId = riderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getGoodsAmountCents() { return goodsAmountCents; }
    public void setGoodsAmountCents(Integer goodsAmountCents) { this.goodsAmountCents = goodsAmountCents; }
    public Integer getFreightCents() { return freightCents; }
    public void setFreightCents(Integer freightCents) { this.freightCents = freightCents; }
    public String getFreightStrategy() { return freightStrategy; }
    public void setFreightStrategy(String freightStrategy) { this.freightStrategy = freightStrategy; }
    public Integer getPenaltyCents() { return penaltyCents; }
    public void setPenaltyCents(Integer penaltyCents) { this.penaltyCents = penaltyCents; }
    public Double getUserLat() { return userLat; }
    public void setUserLat(Double userLat) { this.userLat = userLat; }
    public Double getUserLon() { return userLon; }
    public void setUserLon(Double userLon) { this.userLon = userLon; }
    public Double getMerchantLat() { return merchantLat; }
    public void setMerchantLat(Double merchantLat) { this.merchantLat = merchantLat; }
    public Double getMerchantLon() { return merchantLon; }
    public void setMerchantLon(Double merchantLon) { this.merchantLon = merchantLon; }
    public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public void setSkuSnapshot(String skuSnapshot) { this.skuSnapshot = skuSnapshot; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }
    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Integer getPayAmountCents() { return payAmountCents; }
    public void setPayAmountCents(Integer payAmountCents) { this.payAmountCents = payAmountCents; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public String getRefundRejectReason() { return refundRejectReason; }
    public void setRefundRejectReason(String refundRejectReason) { this.refundRejectReason = refundRejectReason; }
    public String getResumeStatus() { return resumeStatus; }
    public void setResumeStatus(String resumeStatus) { this.resumeStatus = resumeStatus; }
}
