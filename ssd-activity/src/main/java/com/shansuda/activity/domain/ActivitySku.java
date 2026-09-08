package com.shansuda.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "activity_sku")
public class ActivitySku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "activity_id")
    private Long activityId;
    @Column(name = "sku_id")
    private Long skuId;
    private String name;
    @Column(name = "price_cents")
    private Integer priceCents;
    @Column(name = "origin_stock")
    private Integer originStock;
    @Column(name = "origin_price_cents")
    private Integer originPriceCents;
    @Column(name = "image_url")
    private String imageUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }
    public Integer getOriginStock() { return originStock; }
    public void setOriginStock(Integer originStock) { this.originStock = originStock; }
    public Integer getOriginPriceCents() { return originPriceCents; }
    public void setOriginPriceCents(Integer originPriceCents) { this.originPriceCents = originPriceCents; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
