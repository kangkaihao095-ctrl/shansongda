package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rider")
public class Rider {

    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "online_status")
    private String onlineStatus;
    @Column(name = "accept_status")
    private String acceptStatus;
    private Double lat;
    private Double lon;
    @Column(name = "update_time")
    private Instant updateTime;
    private Long version;
    @Column(name = "auto_report")
    private Boolean autoReport;
    @Column(name = "auto_report_interval_sec")
    private Integer autoReportIntervalSec;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOnlineStatus() { return onlineStatus; }
    public void setOnlineStatus(String onlineStatus) { this.onlineStatus = onlineStatus; }
    public String getAcceptStatus() { return acceptStatus; }
    public void setAcceptStatus(String acceptStatus) { this.acceptStatus = acceptStatus; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public Instant getUpdateTime() { return updateTime; }
    public void setUpdateTime(Instant updateTime) { this.updateTime = updateTime; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Boolean getAutoReport() { return Boolean.TRUE.equals(autoReport); }
    public void setAutoReport(Boolean autoReport) { this.autoReport = Boolean.TRUE.equals(autoReport); }
    public Integer getAutoReportIntervalSec() {
        int n = autoReportIntervalSec == null ? 5 : autoReportIntervalSec;
        return Math.min(30, Math.max(3, n));
    }
    public void setAutoReportIntervalSec(Integer autoReportIntervalSec) {
        this.autoReportIntervalSec = autoReportIntervalSec;
    }
}
