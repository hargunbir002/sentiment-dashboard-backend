package com.sentimentdashboard.sentiment_dashboard.service;

import com.sentimentdashboard.sentiment_dashboard.entity.Review;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Aspect-based sentiment: finds which product aspects (battery, sound, price...) each
 * review talks about, and how positive or negative the customer is about each one.
 *
 * Method: split each review into clauses at punctuation and contrast words
 * ("but", "however", "and"), find aspect keywords in each clause, then score that
 * clause with the same lexicon engine used for whole reviews.
 */
@Service
public class AspectService {

    private final SentimentService sentimentService;

    public AspectService(SentimentService sentimentService) {
        this.sentimentService = sentimentService;
    }

    private static final Map<String, List<String>> ASPECTS = new LinkedHashMap<>();

    static {
        ASPECTS.put("Battery", List.of("battery", "charge", "charging", "backup", "power"));
        ASPECTS.put("Sound", List.of("sound", "audio", "bass", "volume", "speaker", "mic"));
        ASPECTS.put("Price", List.of("price", "cost", "value", "expensive", "cheap", "affordable", "money"));
        ASPECTS.put("Delivery", List.of("delivery", "shipping", "shipped", "packaging", "courier"));
        ASPECTS.put("Comfort", List.of("comfort", "comfortable", "fit", "fitting", "wear"));
        ASPECTS.put("Quality", List.of("quality", "build", "material", "finish"));
        ASPECTS.put("Durability", List.of("durable", "durability", "sturdy", "broke", "broken", "lasting", "lasted"));
        ASPECTS.put("Design", List.of("design", "look", "looks", "color", "colour", "style"));
    }

    // Split at punctuation or contrast words, so "battery is poor but sound is great" becomes two clauses.
    private static final Pattern CLAUSE_SPLIT = Pattern.compile(
            "[.,;!?]+|\\s+\\b(?:but|however|although|though|and)\\b\\s+",
            Pattern.CASE_INSENSITIVE);

    public List<AspectStat> analyze(List<Review> reviews) {
        Map<String, int[]> counts = new LinkedHashMap<>(); // [positive, negative, neutral]
        for (String aspect : ASPECTS.keySet()) {
            counts.put(aspect, new int[3]);
        }

        for (Review review : reviews) {
            if (review.getReviewText() == null) continue;
            String text = review.getReviewText().toLowerCase().replace("'", "");

            for (String clause : CLAUSE_SPLIT.split(text)) {
                String c = clause.trim();
                if (c.isEmpty()) continue;

                Set<String> aspects = aspectsIn(c);
                if (aspects.isEmpty()) continue;

                String label = sentimentService.analyze(c).getLabel();
                int slot = "POSITIVE".equals(label) ? 0 : "NEGATIVE".equals(label) ? 1 : 2;
                for (String aspect : aspects) {
                    counts.get(aspect)[slot]++;
                }
            }
        }

        List<AspectStat> result = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] c = entry.getValue();
            int total = c[0] + c[1] + c[2];
            if (total == 0) continue; // only aspects that were actually mentioned

            result.add(new AspectStat(
                    entry.getKey(),
                    total,
                    c[0],
                    c[1],
                    c[2],
                    percent(c[0], total),
                    percent(c[1], total),
                    percent(c[2], total),
                    overall(c[0], c[1])
            ));
        }

        result.sort(Comparator.comparingInt(AspectStat::getMentions).reversed());
        return result;
    }

    private Set<String> aspectsIn(String clause) {
        String cleaned = clause.replaceAll("[^a-z0-9\\s]", " ").trim();
        Set<String> tokens = new HashSet<>(Arrays.asList(cleaned.split("\\s+")));

        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : ASPECTS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (tokens.contains(keyword)) {
                    found.add(entry.getKey());
                    break;
                }
            }
        }
        return found;
    }

    private double percent(int part, int total) {
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String overall(int positive, int negative) {
        if (positive > negative) return "POSITIVE";
        if (negative > positive) return "NEGATIVE";
        return "NEUTRAL";
    }

    // ---------- Response DTO ----------

    public static class AspectStat {
        private final String aspect;
        private final int mentions;
        private final int positive;
        private final int negative;
        private final int neutral;
        private final double positivePercent;
        private final double negativePercent;
        private final double neutralPercent;
        private final String overallSentiment;

        public AspectStat(String aspect, int mentions, int positive, int negative, int neutral,
                          double positivePercent, double negativePercent, double neutralPercent,
                          String overallSentiment) {
            this.aspect = aspect;
            this.mentions = mentions;
            this.positive = positive;
            this.negative = negative;
            this.neutral = neutral;
            this.positivePercent = positivePercent;
            this.negativePercent = negativePercent;
            this.neutralPercent = neutralPercent;
            this.overallSentiment = overallSentiment;
        }

        public String getAspect() { return aspect; }
        public int getMentions() { return mentions; }
        public int getPositive() { return positive; }
        public int getNegative() { return negative; }
        public int getNeutral() { return neutral; }
        public double getPositivePercent() { return positivePercent; }
        public double getNegativePercent() { return negativePercent; }
        public double getNeutralPercent() { return neutralPercent; }
        public String getOverallSentiment() { return overallSentiment; }
    }
}