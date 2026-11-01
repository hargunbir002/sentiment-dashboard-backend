package com.sentimentdashboard.sentiment_dashboard.controller;

import com.sentimentdashboard.sentiment_dashboard.entity.Product;
import com.sentimentdashboard.sentiment_dashboard.entity.Review;
import com.sentimentdashboard.sentiment_dashboard.entity.SentimentResult;
import com.sentimentdashboard.sentiment_dashboard.repository.ProductRepository;
import com.sentimentdashboard.sentiment_dashboard.repository.ReviewRepository;
import com.sentimentdashboard.sentiment_dashboard.repository.SentimentResultRepository;
import com.sentimentdashboard.sentiment_dashboard.service.CsvParser;
import com.sentimentdashboard.sentiment_dashboard.service.SentimentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * POST /api/reviews/upload  (multipart: file, optional productId)
 * CSV header (case-insensitive): review_text (or review), rating, reviewer_name (optional), product (optional)
 * Product is taken from the "product" column (matched by name, case-insensitive) or, if missing, from productId.
 * Products named in the CSV that do not exist yet are created automatically (createMissingProducts=true, default).
 */
@RestController
@RequestMapping("/api/reviews/upload")
public class ReviewUploadController {

    private static final int MAX_ROWS = 1000;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final SentimentResultRepository sentimentResultRepository;
    private final SentimentService sentimentService;

    public ReviewUploadController(ReviewRepository reviewRepository,
                                  ProductRepository productRepository,
                                  SentimentResultRepository sentimentResultRepository,
                                  SentimentService sentimentService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.sentimentResultRepository = sentimentResultRepository;
        this.sentimentService = sentimentService;
    }

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "productId", required = false) Long productId,
                                    @RequestParam(value = "createMissingProducts", defaultValue = "true") boolean createMissingProducts) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        }

        List<List<String>> rows;
        try {
            rows = CsvParser.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Could not read file"));
        }
        if (rows.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("message", "CSV needs a header row and at least one review"));
        }
        if (rows.size() - 1 > MAX_ROWS) {
            return ResponseEntity.badRequest().body(Map.of("message", "Max " + MAX_ROWS + " reviews per upload"));
        }

        // header -> column index
        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim().toLowerCase().replace(' ', '_'), i);
        }
        Integer textCol = col.containsKey("review_text") ? col.get("review_text") : col.get("review");
        Integer ratingCol = col.get("rating");
        Integer nameCol = col.containsKey("reviewer_name") ? col.get("reviewer_name") : col.get("reviewer");
        Integer productCol = col.containsKey("product") ? col.get("product") : col.get("product_name");

        if (textCol == null || ratingCol == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Header must contain: review_text, rating (optional: reviewer_name, product)"));
        }

        Product defaultProduct = null;
        if (productId != null) {
            defaultProduct = productRepository.findById(productId).orElse(null);
            if (defaultProduct == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid productId"));
            }
        }
        if (defaultProduct == null && productCol == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Select a product, or add a 'product' column to the CSV"));
        }

        Map<String, Product> byName = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            if (p.getName() != null) byName.put(p.getName().trim().toLowerCase(), p);
        }

        int imported = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<String> createdProducts = new ArrayList<>();

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int line = r + 1; // line number as seen in a spreadsheet
            String text = cell(row, textCol);
            String ratingStr = cell(row, ratingCol);

            if (text.isEmpty()) { errors.add(err(line, "review_text is empty")); continue; }
            int rating;
            try {
                rating = Integer.parseInt(ratingStr);
            } catch (NumberFormatException e) {
                errors.add(err(line, "rating must be a number 1-5")); continue;
            }
            if (rating < 1 || rating > 5) { errors.add(err(line, "rating must be between 1 and 5")); continue; }

            Product product = defaultProduct;
            if (productCol != null && !cell(row, productCol).isEmpty()) {
                String productName = cell(row, productCol);
                product = byName.get(productName.toLowerCase());
                if (product == null) {
                    if (!createMissingProducts) {
                        errors.add(err(line, "unknown product '" + productName + "'"));
                        continue;
                    }
                    Product created = new Product();
                    created.setName(productName);
                    product = productRepository.save(created);
                    byName.put(productName.toLowerCase(), product);
                    createdProducts.add(productName);
                }
            }
            if (product == null) { errors.add(err(line, "no product")); continue; }

            Review review = new Review();
            review.setProduct(product);
            review.setReviewText(text);
            review.setRating(rating);
            String reviewer = nameCol == null ? "" : cell(row, nameCol);
            review.setReviewerName(reviewer.isEmpty() ? "Anonymous" : reviewer);
            Review saved = reviewRepository.save(review);

            SentimentService.SentimentAnalysisResult a = sentimentService.analyze(text);
            SentimentResult sr = new SentimentResult();
            sr.setReview(saved);
            sr.setSentimentLabel(a.getLabel());
            sr.setSentimentScore(a.getScore());
            sr.setConfidence(a.getConfidence());
            sr.setKeywords(a.getKeywords());
            sentimentResultRepository.save(sr);
            imported++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size() - 1);
        result.put("imported", imported);
        result.put("skipped", errors.size());
        result.put("createdProducts", createdProducts);
        result.put("errors", errors.size() > 20 ? errors.subList(0, 20) : errors);
        return ResponseEntity.ok(result);
    }

    private static String cell(List<String> row, Integer idx) {
        if (idx == null || idx >= row.size()) return "";
        return row.get(idx).trim();
    }

    private static Map<String, Object> err(int line, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("line", line);
        m.put("message", msg);
        return m;
    }
}