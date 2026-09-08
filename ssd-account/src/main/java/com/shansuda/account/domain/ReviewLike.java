package com.shansuda.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "review_like")
@IdClass(ReviewLike.Pk.class)
public class ReviewLike {

    @Id
    @Column(name = "user_id")
    private Long userId;
    @Id
    @Column(name = "review_id")
    private Long reviewId;
    @Column(name = "created_at")
    private Instant createdAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class Pk implements Serializable {
        private Long userId;
        private Long reviewId;

        public Pk() {
        }

        public Pk(Long userId, Long reviewId) {
            this.userId = userId;
            this.reviewId = reviewId;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getReviewId() { return reviewId; }
        public void setReviewId(Long reviewId) { this.reviewId = reviewId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(userId, pk.userId) && Objects.equals(reviewId, pk.reviewId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, reviewId);
        }
    }
}
