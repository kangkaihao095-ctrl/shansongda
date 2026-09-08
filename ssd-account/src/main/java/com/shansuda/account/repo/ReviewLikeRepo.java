package com.shansuda.account.repo;

import com.shansuda.account.domain.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLikeRepo extends JpaRepository<ReviewLike, ReviewLike.Pk> {
    boolean existsByUserIdAndReviewId(Long userId, Long reviewId);

    void deleteByUserIdAndReviewId(Long userId, Long reviewId);
}
