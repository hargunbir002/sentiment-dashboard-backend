package com.sentimentdashboard.sentiment_dashboard.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "review_text", nullable = false, columnDefinition = "TEXT")
    private String reviewText;

    @Column
    private Integer rating;

    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Not @JsonIgnore here - we want sentiment included when a review is returned.
    // The loop is broken on the other side (SentimentResult.review is @JsonIgnore).
    @OneToOne(mappedBy = "review", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SentimentResult sentimentResult;

    public Review() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) { this.reviewText = reviewText; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public SentimentResult getSentimentResult() { return sentimentResult; }
    public void setSentimentResult(SentimentResult sentimentResult) { this.sentimentResult = sentimentResult; }
}