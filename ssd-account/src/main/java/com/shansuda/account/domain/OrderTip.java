package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "order_tip")
public class OrderTip {

    @Id
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "rider_id")
    private Long riderId;
    @Column(name = "user_id")
    private Long userId;
    private Integer cents;
    @Column(name = "gift_code")
    private String giftCode;
    @Column(name = "created_at")
    private Instant createdAt;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRiderId() { return riderId; }
    public void setRiderId(Long riderId) { this.riderId = riderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getCents() { return cents; }
    public void setCents(Integer cents) { this.cents = cents; }
    public String getGiftCode() { return giftCode; }
    public void setGiftCode(String giftCode) { this.giftCode = giftCode; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
