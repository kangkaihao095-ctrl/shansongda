package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon_template")
public class CouponTemplate {

    @Id
    private String code;
    private String name;
    private String scene;
    private String type;
    @Column(name = "min_spend_cents")
    private Integer minSpendCents;
    @Column(name = "discount_cents")
    private Integer discountCents;
    @Column(name = "percent_off")
    private Integer percentOff;
    @Column(name = "member_only")
    private Boolean memberOnly;
    private String icon;
    @Column(name = "valid_seconds")
    private Integer validSeconds;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getMinSpendCents() { return minSpendCents; }
    public void setMinSpendCents(Integer minSpendCents) { this.minSpendCents = minSpendCents; }
    public Integer getDiscountCents() { return discountCents; }
    public void setDiscountCents(Integer discountCents) { this.discountCents = discountCents; }
    public Integer getPercentOff() { return percentOff; }
    public void setPercentOff(Integer percentOff) { this.percentOff = percentOff; }
    public Boolean getMemberOnly() { return memberOnly; }
    public void setMemberOnly(Boolean memberOnly) { this.memberOnly = memberOnly; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getValidSeconds() { return validSeconds; }
    public void setValidSeconds(Integer validSeconds) { this.validSeconds = validSeconds; }
}
