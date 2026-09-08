package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchant_sku")
public class MerchantSku {

    @Id
    private Long id;
    @Column(name = "merchant_id")
    private Long merchantId;
    private String name;
    @Column(name = "group_name")
    private String groupName;
    @Column(name = "price_cents")
    private Integer priceCents;
    @Column(name = "origin_price_cents")
    private Integer originPriceCents;
    @Column(name = "image_url")
    private String imageUrl;
    private String spec;
    private Integer stock;
    private String status;
    private String description;
    private String detail;
    @Column(name = "month_sales")
    private Integer monthSales;
    @Column(name = "like_count")
    private Integer likeCount;
    @Column(name = "sku_key")
    private String skuKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }
    public Integer getOriginPriceCents() { return originPriceCents; }
    public void setOriginPriceCents(Integer originPriceCents) { this.originPriceCents = originPriceCents; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Integer getMonthSales() { return monthSales; }
    public void setMonthSales(Integer monthSales) { this.monthSales = monthSales; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public String getSkuKey() { return skuKey; }
    public void setSkuKey(String skuKey) { this.skuKey = skuKey; }
}
