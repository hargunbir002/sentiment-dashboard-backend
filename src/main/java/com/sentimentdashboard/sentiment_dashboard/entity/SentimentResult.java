package com.sentimentdashboard.sentiment_dashboard.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sentiment_results")
public class SentimentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false, unique = true)
    private Review review;

    @Column(name = "sentiment_label", nullable = false, length = 20)
    private String sentimentLabel; // POSITIVE / NEGATIVE / NEUTRAL

    @Column(name = "sentiment_score", precision = 5, scale = 4)
    private BigDecimal sentimentScore;

    // 0.0 to 1.0 - how strongly the keywords agree with the label
    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(length = 500)
    private String keywords;

    @Column(name = "processed_at", updatable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    public SentimentResult() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Review getReview() { return review; }
    public void setReview(Review review) { this.review = review; }

    public String getSentimentLabel() { return sentimentLabel; }
    public void setSentimentLabel(String sentimentLabel) { this.sentimentLabel = sentimentLabel; }

    public BigDecimal getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(BigDecimal sentimentScore) { this.sentimentScore = sentimentScore; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}