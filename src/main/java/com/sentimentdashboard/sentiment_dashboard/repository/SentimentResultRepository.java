package com.sentimentdashboard.sentiment_dashboard.repository;

import com.sentimentdashboard.sentiment_dashboard.entity.SentimentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SentimentResultRepository extends JpaRepository<SentimentResult, Long> {

    Optional<SentimentResult> findByReviewId(Long reviewId);

    List<SentimentResult> findBySentimentLabel(String sentimentLabel);

    // Overall sentiment distribution
    @Query("SELECT sr.sentimentLabel, COUNT(sr) FROM SentimentResult sr GROUP BY sr.sentimentLabel")
    List<Object[]> countBySentimentLabel();

    // Product-wise sentiment distribution
    @Query("SELECT r.product.id, sr.sentimentLabel, COUNT(sr) " +
            "FROM SentimentResult sr JOIN sr.review r " +
            "GROUP BY r.product.id, sr.sentimentLabel")
    List<Object[]> countByProductAndSentiment();

    // Sentiment trend by month
    @Query("SELECT FUNCTION('DATE_FORMAT', r.createdAt, '%Y-%m') AS month, sr.sentimentLabel, COUNT(sr) " +
            "FROM SentimentResult sr JOIN sr.review r " +
            "GROUP BY month, sr.sentimentLabel ORDER BY month")
    List<Object[]> countByMonthAndSentiment();

    // Rating vs sentiment cross-check
    @Query("SELECT r.rating, sr.sentimentLabel, COUNT(sr) " +
            "FROM SentimentResult sr JOIN sr.review r " +
            "GROUP BY r.rating, sr.sentimentLabel")
    List<Object[]> countByRatingAndSentiment();

    // All stored keyword strings (comma-separated), used for complaint / feature analysis
    @Query("SELECT sr.keywords FROM SentimentResult sr WHERE sr.keywords IS NOT NULL AND sr.keywords <> ''")
    List<String> findAllKeywords();
}