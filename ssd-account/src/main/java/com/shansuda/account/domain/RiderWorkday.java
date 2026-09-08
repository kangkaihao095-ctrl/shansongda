package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "rider_workday")
@IdClass(RiderWorkday.Key.class)
public class RiderWorkday {

    @Id
    @Column(name = "user_id")
    private Long userId;
    @Id
    @Column(name = "work_date")
    private LocalDate workDate;
    @Column(name = "worked_seconds")
    private Integer workedSeconds;
    @Column(name = "forced_offline")
    private Boolean forcedOffline;
    @Column(name = "last_tick_at")
    private Instant lastTickAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public Integer getWorkedSeconds() { return workedSeconds == null ? 0 : workedSeconds; }
    public void setWorkedSeconds(Integer workedSeconds) { this.workedSeconds = workedSeconds; }
    public Boolean getForcedOffline() { return Boolean.TRUE.equals(forcedOffline); }
    public void setForcedOffline(Boolean forcedOffline) { this.forcedOffline = forcedOffline; }
    public Instant getLastTickAt() { return lastTickAt; }
    public void setLastTickAt(Instant lastTickAt) { this.lastTickAt = lastTickAt; }

    public static class Key implements Serializable {
        private Long userId;
        private LocalDate workDate;

        public Key() {
        }

        public Key(Long userId, LocalDate workDate) {
            this.userId = userId;
            this.workDate = workDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(userId, key.userId) && Objects.equals(workDate, key.workDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, workDate);
        }
    }
}
