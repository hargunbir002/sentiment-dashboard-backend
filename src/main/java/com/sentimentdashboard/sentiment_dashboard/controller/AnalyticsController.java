package com.sentimentdashboard.sentiment_dashboard.controller;

import com.sentimentdashboard.sentiment_dashboard.repository.SentimentResultRepository;
import com.sentimentdashboard.sentiment_dashboard.service.SentimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private SentimentResultRepository sentimentResultRepository;

    @Autowired
    private SentimentService sentimentService;

    // KPI: total reviews + overall sentiment breakdown
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        List<Object[]> rows = sentimentResultRepository.countBySentimentLabel();

        Map<String, Long> breakdown = new HashMap<>();
        long total = 0;
        for (Object[] row : rows) {
            Long count = (Long) row[1];
            breakdown.put((String) row[0], count);
            total += count;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReviews", total);
        result.put("sentimentBreakdown", breakdown);
        return result;
    }

    // Pie chart: sentiment distribution
    @GetMapping("/sentiment-distribution")
    public List<Map<String, Object>> getSentimentDistribution() {
        return sentimentResultRepository.countBySentimentLabel().stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("label", row[0]);
            entry.put("count", row[1]);
            return entry;
        }).collect(Collectors.toList());
    }

    // Line chart: monthly trend
    @GetMapping("/trend")
    public List<Map<String, Object>> getTrend() {
        return sentimentResultRepository.countByMonthAndSentiment().stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month", row[0]);
            entry.put("label", row[1]);
            entry.put("count", row[2]);
            return entry;
        }).collect(Collectors.toList());
    }

    // Stacked bar: product-wise sentiment
    @GetMapping("/product-sentiment")
    public List<Map<String, Object>> getProductSentiment() {
        return sentimentResultRepository.countByProductAndSentiment().stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId", row[0]);
            entry.put("label", row[1]);
            entry.put("count", row[2]);
            return entry;
        }).collect(Collectors.toList());
    }

    // Grouped bar: rating vs sentiment
    @GetMapping("/rating-vs-sentiment")
    public List<Map<String, Object>> getRatingVsSentiment() {
        return sentimentResultRepository.countByRatingAndSentiment().stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rating", row[0]);
            entry.put("label", row[1]);
            entry.put("count", row[2]);
            return entry;
        }).collect(Collectors.toList());
    }

    // Top complaints (negative keywords) and most liked features (positive keywords)
    // GET /api/analytics/keywords?limit=10
    @GetMapping("/keywords")
    public Map<String, Object> getKeywords(@RequestParam(defaultValue = "10") int limit) {
        Map<String, Integer> positive = new HashMap<>();
        Map<String, Integer> negative = new HashMap<>();

        for (String csv : sentimentResultRepository.findAllKeywords()) {
            for (String raw : csv.split(",")) {
                String keyword = raw.trim();
                if (keyword.isEmpty()) continue;

                String polarity = sentimentService.polarityOf(keyword);
                if ("POSITIVE".equals(polarity)) positive.merge(keyword, 1, Integer::sum);
                else if ("NEGATIVE".equals(polarity)) negative.merge(keyword, 1, Integer::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topComplaints", topN(negative, limit));
        result.put("topLikedFeatures", topN(positive, limit));
        return result;
    }

    private List<Map<String, Object>> topN(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("word", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }
}