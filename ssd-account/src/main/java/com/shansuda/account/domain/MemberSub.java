package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "member_sub")
public class MemberSub {

    @Id
    @Column(name = "user_id")
    private Long userId;
    private String plan;
    private Integer level;
    @Column(name = "paid_cents_net")
    private Integer paidCentsNet;
    @Column(name = "expire_at")
    private Instant expireAt;
    private String status;
    @Column(name = "year_member")
    private Boolean yearMember;
    @Column(name = "auto_renew")
    private Boolean autoRenew;
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getPaidCentsNet() { return paidCentsNet; }
    public void setPaidCentsNet(Integer paidCentsNet) { this.paidCentsNet = paidCentsNet; }
    public Instant getExpireAt() { return expireAt; }
    public void setExpireAt(Instant expireAt) { this.expireAt = expireAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getYearMember() { return yearMember; }
    public void setYearMember(Boolean yearMember) { this.yearMember = yearMember; }
    public Boolean getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
