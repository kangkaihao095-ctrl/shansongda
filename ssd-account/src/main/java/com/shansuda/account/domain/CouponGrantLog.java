package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "coupon_grant_log")
public class CouponGrantLog {

    @Id
    @Column(name = "grant_key")
    private String grantKey;
    @Column(name = "user_id")
    private Long userId;
    private String scene;
    @Column(name = "created_at")
    private Instant createdAt;

    public String getGrantKey() { return grantKey; }
    public void setGrantKey(String grantKey) { this.grantKey = grantKey; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
