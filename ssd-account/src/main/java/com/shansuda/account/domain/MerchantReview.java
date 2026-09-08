package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "merchant_review")
public class MerchantReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "merchant_id")
    private Long merchantId;
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "user_id")
    private Long userId;
    private Integer score;
    private String content;
    @Column(name = "sku_names")
    private String skuNames;
    @Column(name = "like_count")
    private Integer likeCount;
    @Column(name = "photo_urls")
    private String photoUrls;
    @Column(name = "rider_score")
    private Integer riderScore;
    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSkuNames() { return skuNames; }
    public void setSkuNames(String skuNames) { this.skuNames = skuNames; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public String getPhotoUrls() { return photoUrls; }
    public void setPhotoUrls(String photoUrls) { this.photoUrls = photoUrls; }
    public Integer getRiderScore() { return riderScore; }
    public void setRiderScore(Integer riderScore) { this.riderScore = riderScore; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
