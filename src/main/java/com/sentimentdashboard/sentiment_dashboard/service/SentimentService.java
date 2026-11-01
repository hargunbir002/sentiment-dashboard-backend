package com.sentimentdashboard.sentiment_dashboard.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class SentimentService {

    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "excellent", "good", "great", "amazing", "awesome", "love", "best",
            "perfect", "fantastic", "wonderful", "nice", "happy", "satisfied",
            "durable", "comfortable", "recommend", "quality", "smooth", "fast",
            "affordable", "reliable", "impressive", "beautiful", "superb", "worth",
            "value", "clear", "loud", "comfy", "sturdy"
    ));

    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(Arrays.asList(
            "poor", "bad", "worst", "terrible", "broken", "defective", "waste",
            "disappointing", "disappointed", "slow", "useless", "cheap",
            "uncomfortable", "faulty", "damaged", "horrible", "awful",
            "overpriced", "fake", "stopped", "problem", "issue", "complaint",
            "delay", "late", "dead", "weak", "flimsy", "noisy", "rude"
    ));

    // Words that flip the meaning of the next sentiment word ("not good", "never worked")
    private static final Set<String> NEGATORS = new HashSet<>(Arrays.asList(
            "not", "no", "never", "dont", "didnt", "doesnt", "isnt", "wasnt", "cant"
    ));

    // Generic stopwords. Negators are intentionally NOT here.
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "is", "a", "an", "and", "or", "but", "in", "on", "at",
            "to", "for", "of", "with", "this", "that", "it", "was", "are",
            "i", "my", "very", "so", "product", "item", "its"
    ));

    public SentimentAnalysisResult analyze(String reviewText) {
        List<String> tokens = tokenize(reviewText);

        int positiveCount = 0;
        int negativeCount = 0;
        List<String> matchedKeywords = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i);
            boolean isPositive = POSITIVE_WORDS.contains(word);
            boolean isNegative = NEGATIVE_WORDS.contains(word);
            if (!isPositive && !isNegative) continue;

            boolean negated = i > 0 && NEGATORS.contains(tokens.get(i - 1));
            boolean effectivelyPositive = isPositive != negated;

            if (effectivelyPositive) positiveCount++;
            else negativeCount++;

            matchedKeywords.add(negated ? tokens.get(i - 1) + " " + word : word);
        }

        String label;
        if (positiveCount > negativeCount) {
            label = "POSITIVE";
        } else if (negativeCount > positiveCount) {
            label = "NEGATIVE";
        } else {
            label = "NEUTRAL";
        }

        int totalWords = Math.max(tokens.size(), 1);
        double rawScore = (double) (positiveCount - negativeCount) / totalWords;
        BigDecimal score = BigDecimal.valueOf(rawScore).setScale(4, RoundingMode.HALF_UP);

        // Confidence: how one-sided the sentiment words are (0 = mixed or no signal, 1 = all agree)
        int signalCount = positiveCount + negativeCount;
        double rawConfidence = signalCount == 0 ? 0.0
                : (double) Math.abs(positiveCount - negativeCount) / signalCount;
        BigDecimal confidence = BigDecimal.valueOf(rawConfidence).setScale(4, RoundingMode.HALF_UP);

        return new SentimentAnalysisResult(label, score, confidence, String.join(", ", matchedKeywords));
    }

    /**
     * Returns "POSITIVE", "NEGATIVE", or null for a stored keyword.
     * Keywords look like "good", "poor", or "not good" (negated).
     */
    public String polarityOf(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String kw = keyword.trim();
        boolean negated = kw.startsWith("not ") || kw.startsWith("no ") || kw.startsWith("never ");
        String base = negated ? kw.substring(kw.indexOf(' ') + 1) : kw;

        boolean basePositive = POSITIVE_WORDS.contains(base);
        boolean baseNegative = NEGATIVE_WORDS.contains(base);
        if (!basePositive && !baseNegative) return null;

        boolean positive = basePositive != negated;
        return positive ? "POSITIVE" : "NEGATIVE";
    }

    private List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();

        String cleaned = text.toLowerCase()
                .replace("'", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim();

        List<String> result = new ArrayList<>();
        for (String w : cleaned.split("\\s+")) {
            if (!w.isEmpty() && !STOPWORDS.contains(w)) {
                result.add(w);
            }
        }
        return result;
    }

    public static class SentimentAnalysisResult {
        private final String label;
        private final BigDecimal score;
        private final BigDecimal confidence;
        private final String keywords;

        public SentimentAnalysisResult(String label, BigDecimal score, BigDecimal confidence, String keywords) {
            this.label = label;
            this.score = score;
            this.confidence = confidence;
            this.keywords = keywords;
        }

        public String getLabel() { return label; }
        public BigDecimal getScore() { return score; }
        public BigDecimal getConfidence() { return confidence; }
        public String getKeywords() { return keywords; }
    }
}