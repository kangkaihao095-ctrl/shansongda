package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchant")
public class Merchant {

    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "shop_name")
    private String shopName;
    private Double lat;
    private Double lon;
    private String address;
    private String category;
    @Column(name = "cover_url")
    private String coverUrl;
    private Double rating;
    private String promo;
    @Column(name = "online_status")
    private String onlineStatus;
    @Column(name = "rating_avg")
    private Double ratingAvg;
    @Column(name = "rating_count")
    private Integer ratingCount;
    @Column(name = "completed_count")
    private Integer completedCount;
    @Column(name = "auto_accept")
    private Boolean autoAccept;
    private String intro;
    private String phone;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getPromo() { return promo; }
    public void setPromo(String promo) { this.promo = promo; }
    public String getOnlineStatus() { return onlineStatus; }
    public void setOnlineStatus(String onlineStatus) { this.onlineStatus = onlineStatus; }
    public Double getRatingAvg() { return ratingAvg; }
    public void setRatingAvg(Double ratingAvg) { this.ratingAvg = ratingAvg; }
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    public Boolean getAutoAccept() { return autoAccept; }
    public void setAutoAccept(Boolean autoAccept) { this.autoAccept = autoAccept; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
