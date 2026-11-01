package com.sentimentdashboard.sentiment_dashboard.controller;

import com.sentimentdashboard.sentiment_dashboard.entity.Product;
import com.sentimentdashboard.sentiment_dashboard.entity.Review;
import com.sentimentdashboard.sentiment_dashboard.entity.SentimentResult;
import com.sentimentdashboard.sentiment_dashboard.repository.ProductRepository;
import com.sentimentdashboard.sentiment_dashboard.repository.ReviewRepository;
import com.sentimentdashboard.sentiment_dashboard.repository.SentimentResultRepository;
import com.sentimentdashboard.sentiment_dashboard.service.SentimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SentimentResultRepository sentimentResultRepository;

    @Autowired
    private SentimentService sentimentService;

    @GetMapping
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    @GetMapping("/product/{productId}")
    public List<Review> getReviewsByProduct(@PathVariable Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Review> getReviewById(@PathVariable Long id) {
        return reviewRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Review Management: all reviews with product name + sentiment.
     * Supports: search (text / product / reviewer), sentiment filter, product filter, sorting.
     * GET /api/reviews/details?search=battery&sentiment=NEGATIVE&productId=1&sort=rating_desc
     * sort options: date_desc (default), date_asc, rating_desc, rating_asc
     */
    @GetMapping("/details")
    public List<ReviewDetailDto> getReviewDetails(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "date_desc") String sort) {

        String q = search == null ? "" : search.trim().toLowerCase();
        String s = sentiment == null ? "" : sentiment.trim().toUpperCase();

        List<ReviewDetailDto> list = reviewRepository.findAll().stream()
                .filter(r -> productId == null || (r.getProduct() != null && productId.equals(r.getProduct().getId())))
                .map(ReviewDetailDto::from)
                .filter(d -> s.isEmpty() || s.equals(d.sentimentLabel))
                .filter(d -> q.isEmpty()
                        || contains(d.reviewText, q)
                        || contains(d.productName, q)
                        || contains(d.reviewerName, q))
                .collect(Collectors.toList());

        Comparator<ReviewDetailDto> comparator;
        switch (sort) {
            case "date_asc":
                comparator = Comparator.comparing((ReviewDetailDto d) -> d.createdAt);
                break;
            case "rating_desc":
                comparator = Comparator.comparing((ReviewDetailDto d) -> d.rating == null ? 0 : d.rating).reversed();
                break;
            case "rating_asc":
                comparator = Comparator.comparing((ReviewDetailDto d) -> d.rating == null ? 0 : d.rating);
                break;
            default:
                comparator = Comparator.comparing((ReviewDetailDto d) -> d.createdAt).reversed();
        }
        list.sort(comparator);
        return list;
    }

    private boolean contains(String text, String q) {
        return text != null && text.toLowerCase().contains(q);
    }

    // CREATE review -> sentiment analysis (label, score, confidence, keywords) -> saved
    @PostMapping
    public ResponseEntity<?> createReview(@RequestBody ReviewRequest request) {
        Product product = productRepository.findById(request.getProductId()).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body("Invalid productId");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setReviewText(request.getReviewText());
        review.setRating(request.getRating());
        review.setReviewerName(request.getReviewerName());
        Review savedReview = reviewRepository.save(review);

        SentimentService.SentimentAnalysisResult analysis =
                sentimentService.analyze(request.getReviewText());

        SentimentResult sentimentResult = new SentimentResult();
        sentimentResult.setReview(savedReview);
        sentimentResult.setSentimentLabel(analysis.getLabel());
        sentimentResult.setSentimentScore(analysis.getScore());
        sentimentResult.setConfidence(analysis.getConfidence());
        sentimentResult.setKeywords(analysis.getKeywords());
        sentimentResultRepository.save(sentimentResult);

        return ResponseEntity.ok(new ReviewResponse(
                savedReview.getId(),
                savedReview.getReviewText(),
                savedReview.getRating(),
                analysis.getLabel(),
                analysis.getScore(),
                analysis.getConfidence(),
                analysis.getKeywords()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        if (!reviewRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reviewRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- DTOs ----------

    public static class ReviewRequest {
        private Long productId;
        private String reviewText;
        private Integer rating;
        private String reviewerName;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getReviewText() { return reviewText; }
        public void setReviewText(String reviewText) { this.reviewText = reviewText; }

        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }

        public String getReviewerName() { return reviewerName; }
        public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    }

    public static class ReviewResponse {
        public Long reviewId;
        public String reviewText;
        public Integer rating;
        public String sentimentLabel;
        public BigDecimal sentimentScore;
        public BigDecimal confidence;
        public String keywords;

        public ReviewResponse(Long reviewId, String reviewText, Integer rating, String sentimentLabel,
                              BigDecimal sentimentScore, BigDecimal confidence, String keywords) {
            this.reviewId = reviewId;
            this.reviewText = reviewText;
            this.rating = rating;
            this.sentimentLabel = sentimentLabel;
            this.sentimentScore = sentimentScore;
            this.confidence = confidence;
            this.keywords = keywords;
        }
    }

    public static class ReviewDetailDto {
        public Long id;
        public Long productId;
        public String productName;
        public String reviewText;
        public Integer rating;
        public String reviewerName;
        public LocalDateTime createdAt;
        public String sentimentLabel;
        public BigDecimal sentimentScore;
        public BigDecimal confidence;
        public String keywords;

        static ReviewDetailDto from(Review r) {
            ReviewDetailDto d = new ReviewDetailDto();
            d.id = r.getId();
            d.productId = r.getProduct() != null ? r.getProduct().getId() : null;
            d.productName = r.getProduct() != null ? r.getProduct().getName() : null;
            d.reviewText = r.getReviewText();
            d.rating = r.getRating();
            d.reviewerName = r.getReviewerName();
            d.createdAt = r.getCreatedAt();

            SentimentResult sr = r.getSentimentResult();
            if (sr != null) {
                d.sentimentLabel = sr.getSentimentLabel();
                d.sentimentScore = sr.getSentimentScore();
                d.confidence = sr.getConfidence();
                d.keywords = sr.getKeywords();
            }
            return d;
        }
    }
}