package com.sentimentdashboard.sentiment_dashboard.repository;

import com.sentimentdashboard.sentiment_dashboard.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductId(Long productId);

    List<Review> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Review> findByProductIdAndCreatedAtBetween(Long productId, LocalDateTime start, LocalDateTime end);
}