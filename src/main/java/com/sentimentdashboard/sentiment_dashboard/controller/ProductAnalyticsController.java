package com.sentimentdashboard.sentiment_dashboard.controller;

import com.sentimentdashboard.sentiment_dashboard.entity.Product;
import com.sentimentdashboard.sentiment_dashboard.entity.Review;
import com.sentimentdashboard.sentiment_dashboard.entity.SentimentResult;
import com.sentimentdashboard.sentiment_dashboard.repository.ProductRepository;
import com.sentimentdashboard.sentiment_dashboard.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Product-wise analysis:
 *  - GET /api/products/{id}/summary  -> one product's sentiment + rating analysis
 *  - GET /api/products/compare?ids=1,2,3 -> side-by-side summaries
 */
@RestController
@RequestMapping("/api/products")
public class ProductAnalyticsController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @GetMapping("/{id}/summary")
    public ResponseEntity<Map<String, Object>> getProductSummary(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(p -> ResponseEntity.ok(summarize(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/compare")
    public List<Map<String, Object>> compareProducts(@RequestParam List<Long> ids) {
        return ids.stream()
                .map(productRepository::findById)
                .filter(Optional::isPresent)
                .map(opt -> summarize(opt.get()))
                .collect(Collectors.toList());
    }

    private Map<String, Object> summarize(Product p) {
        List<Review> reviews = reviewRepository.findByProductId(p.getId());

        long pos = 0, neg = 0, neu = 0;
        double ratingSum = 0;
        int rated = 0;

        for (Review r : reviews) {
            if (r.getRating() != null) {
                ratingSum += r.getRating();
                rated++;
            }
            SentimentResult sr = r.getSentimentResult();
            if (sr == null) continue;
            switch (sr.getSentimentLabel()) {
                case "POSITIVE": pos++; break;
                case "NEGATIVE": neg++; break;
                default: neu++; break;
            }
        }

        long labeled = pos + neg + neu;
        double base = labeled == 0 ? 1 : labeled;

        String overall;
        if (labeled == 0) overall = "NO_DATA";
        else if (pos > neg && pos >= neu) overall = "POSITIVE";
        else if (neg > pos && neg >= neu) overall = "NEGATIVE";
        else overall = "NEUTRAL";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", p.getId());
        result.put("productName", p.getName());
        result.put("category", p.getCategory());
        result.put("totalReviews", reviews.size());
        result.put("positive", pos);
        result.put("negative", neg);
        result.put("neutral", neu);
        result.put("positivePercent", round(pos * 100.0 / base, 1));
        result.put("negativePercent", round(neg * 100.0 / base, 1));
        result.put("neutralPercent", round(neu * 100.0 / base, 1));
        result.put("netSentiment", round((pos - neg) * 100.0 / base, 1));
        result.put("averageRating", rated == 0 ? 0 : round(ratingSum / rated, 2));
        result.put("overallSentiment", overall);
        return result;
    }

    private double round(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}