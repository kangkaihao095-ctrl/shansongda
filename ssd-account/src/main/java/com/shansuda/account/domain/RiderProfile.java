package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "rider_profile")
public class RiderProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;
    private String bio;
    @Column(name = "started_on")
    private LocalDate startedOn;
    @Column(name = "on_time_rate")
    private Double onTimeRate;
    @Column(name = "tip_cents_total")
    private Integer tipCentsTotal;
    @Column(name = "rating_avg")
    private Double ratingAvg;
    @Column(name = "rating_count")
    private Integer ratingCount;
    @Column(name = "completed_count")
    private Integer completedCount;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public LocalDate getStartedOn() { return startedOn; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public Double getOnTimeRate() { return onTimeRate; }
    public void setOnTimeRate(Double onTimeRate) { this.onTimeRate = onTimeRate; }
    public Integer getTipCentsTotal() { return tipCentsTotal; }
    public void setTipCentsTotal(Integer tipCentsTotal) { this.tipCentsTotal = tipCentsTotal; }
    public Double getRatingAvg() { return ratingAvg; }
    public void setRatingAvg(Double ratingAvg) { this.ratingAvg = ratingAvg; }
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
}
