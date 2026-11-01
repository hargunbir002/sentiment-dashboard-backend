package com.sentimentdashboard.sentiment_dashboard.controller;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sentimentdashboard.sentiment_dashboard.entity.Product;
import com.sentimentdashboard.sentiment_dashboard.entity.Review;
import com.sentimentdashboard.sentiment_dashboard.repository.ProductRepository;
import com.sentimentdashboard.sentiment_dashboard.repository.ReviewRepository;
import com.sentimentdashboard.sentiment_dashboard.service.AspectService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GET /api/reports/reviews.csv?search=&sentiment=&productId=&sort=   -> CSV of (filtered) reviews
 * GET /api/reports/summary.pdf?productId=                            -> PDF summary report
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Color INDIGO = new Color(79, 70, 229);
    private static final Color GREEN = new Color(16, 185, 129);
    private static final Color GREY = new Color(148, 163, 184);
    private static final Color RED = new Color(244, 63, 94);
    private static final Color LIGHT = new Color(241, 245, 249);

    private final ReviewController reviewController;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final AspectService aspectService;

    public ReportController(ReviewController reviewController,
                            ReviewRepository reviewRepository,
                            ProductRepository productRepository,
                            AspectService aspectService) {
        this.reviewController = reviewController;
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.aspectService = aspectService;
    }

    // ------------------------------------------------------------------ CSV

    @GetMapping("/reviews.csv")
    public ResponseEntity<byte[]> reviewsCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "date_desc") String sort) {

        List<ReviewController.ReviewDetailDto> rows =
                reviewController.getReviewDetails(search, sentiment, productId, sort);

        StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel reads UTF-8
        sb.append("id,product,reviewer,rating,review_text,sentiment,score,confidence,keywords,created_at\n");
        for (ReviewController.ReviewDetailDto d : rows) {
            sb.append(csv(d.id)).append(',')
                    .append(csv(d.productName)).append(',')
                    .append(csv(d.reviewerName)).append(',')
                    .append(csv(d.rating)).append(',')
                    .append(csv(d.reviewText)).append(',')
                    .append(csv(d.sentimentLabel)).append(',')
                    .append(csv(d.sentimentScore)).append(',')
                    .append(csv(d.confidence)).append(',')
                    .append(csv(d.keywords)).append(',')
                    .append(csv(d.createdAt)).append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reviews_export.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Quote a CSV value; neutralise spreadsheet formula injection (=, +, -, @ at start of text). */
    private static String csv(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(value instanceof Number)) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ------------------------------------------------------------------ PDF

    @GetMapping("/summary.pdf")
    public ResponseEntity<byte[]> summaryPdf(@RequestParam(required = false) Long productId) throws Exception {
        List<Review> reviews = productId == null
                ? reviewRepository.findAll()
                : reviewRepository.findByProductId(productId);

        String scope = "All products";
        if (productId != null) {
            scope = productRepository.findById(productId).map(Product::getName).orElse("Product " + productId);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 45, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, INDIGO);
        Font sub = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.DARK_GRAY);

        doc.add(new Paragraph("Product Review Sentiment Report", title));
        doc.add(new Paragraph("Scope: " + scope + "   |   Generated: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), sub));
        doc.add(spacer(10));

        // ---- Overall numbers
        Counts all = new Counts();
        for (Review r : reviews) all.add(r);

        doc.add(new Paragraph("Overview", h2));
        doc.add(spacer(4));
        PdfPTable kpi = new PdfPTable(5);
        kpi.setWidthPercentage(100);
        kpi.addCell(kpiCell("Total reviews", String.valueOf(all.total), INDIGO));
        kpi.addCell(kpiCell("Positive", all.pos + " (" + pct(all.pos, all.total) + ")", GREEN));
        kpi.addCell(kpiCell("Neutral", all.neu + " (" + pct(all.neu, all.total) + ")", GREY));
        kpi.addCell(kpiCell("Negative", all.neg + " (" + pct(all.neg, all.total) + ")", RED));
        kpi.addCell(kpiCell("Avg rating", all.ratingN == 0 ? "-" : String.format("%.2f / 5", all.avgRating()), INDIGO));
        doc.add(kpi);

        if (all.pos + all.neu + all.neg > 0) {
            doc.add(spacer(8));
            doc.add(sentimentBar(all));
        }

        // ---- Product-wise
        Map<String, Counts> byProduct = new LinkedHashMap<>();
        for (Review r : reviews) {
            String name = r.getProduct() != null && r.getProduct().getName() != null
                    ? r.getProduct().getName() : "Unknown";
            byProduct.computeIfAbsent(name, k -> new Counts()).add(r);
        }
        if (!byProduct.isEmpty()) {
            doc.add(spacer(16));
            doc.add(new Paragraph("Product-wise sentiment", h2));
            doc.add(spacer(4));
            PdfPTable t = new PdfPTable(new float[]{3f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 1.6f});
            t.setWidthPercentage(100);
            for (String h : new String[]{"Product", "Reviews", "Pos %", "Neu %", "Neg %", "Avg rating", "Overall"}) {
                t.addCell(head(h));
            }
            for (Map.Entry<String, Counts> e : byProduct.entrySet()) {
                Counts c = e.getValue();
                t.addCell(body(e.getKey(), false));
                t.addCell(body(String.valueOf(c.total), true));
                t.addCell(body(pct(c.pos, c.total), true));
                t.addCell(body(pct(c.neu, c.total), true));
                t.addCell(body(pct(c.neg, c.total), true));
                t.addCell(body(c.ratingN == 0 ? "-" : String.format("%.2f", c.avgRating()), true));
                t.addCell(body(c.overall(), true));
            }
            doc.add(t);
        }

        // ---- Aspect-based
        List<AspectService.AspectStat> aspects = aspectService.analyze(reviews);
        if (!aspects.isEmpty()) {
            doc.add(spacer(16));
            doc.add(new Paragraph("Aspect-based sentiment", h2));
            doc.add(spacer(4));
            PdfPTable t = new PdfPTable(new float[]{2.4f, 1.3f, 1.3f, 1.3f, 1.3f, 1.8f});
            t.setWidthPercentage(100);
            for (String h : new String[]{"Aspect", "Mentions", "Pos %", "Neu %", "Neg %", "Overall"}) {
                t.addCell(head(h));
            }
            for (AspectService.AspectStat a : aspects) {
                t.addCell(body(a.getAspect(), false));
                t.addCell(body(String.valueOf(a.getMentions()), true));
                t.addCell(body(String.format("%.0f%%", a.getPositivePercent()), true));
                t.addCell(body(String.format("%.0f%%", a.getNeutralPercent()), true));
                t.addCell(body(String.format("%.0f%%", a.getNegativePercent()), true));
                t.addCell(body(a.getOverallSentiment(), true));
            }
            doc.add(t);
        }

        // ---- Recent negative reviews
        List<Review> negatives = new ArrayList<>();
        for (Review r : reviews) {
            if ("NEGATIVE".equals(label(r))) negatives.add(r);
        }
        negatives.sort(Comparator.comparing(
                (Review r) -> r.getCreatedAt() == null ? LocalDateTime.MIN : r.getCreatedAt()).reversed());
        if (!negatives.isEmpty()) {
            doc.add(spacer(16));
            doc.add(new Paragraph("Recent negative reviews (needs attention)", h2));
            doc.add(spacer(4));
            PdfPTable t = new PdfPTable(new float[]{2f, 0.9f, 6f});
            t.setWidthPercentage(100);
            for (String h : new String[]{"Product", "Rating", "Review"}) t.addCell(head(h));
            for (Review r : negatives.subList(0, Math.min(8, negatives.size()))) {
                t.addCell(body(r.getProduct() != null ? String.valueOf(r.getProduct().getName()) : "-", false));
                t.addCell(body(r.getRating() == null ? "-" : r.getRating() + "/5", true));
                t.addCell(body(truncate(r.getReviewText(), 220), false));
            }
            doc.add(t);
        }

        doc.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sentiment_report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(out.toByteArray());
    }

    // ---------------------------------------------------------- PDF helpers

    private static Paragraph spacer(float h) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(h);
        return p;
    }

    private static PdfPCell kpiCell(String label, String value, Color color) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(LIGHT);
        c.setBorderColor(Color.WHITE);
        c.setBorderWidth(3);
        c.setPadding(8);
        c.addElement(new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)));
        c.addElement(new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, color)));
        return c;
    }

    private static PdfPCell head(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        c.setBackgroundColor(INDIGO);
        c.setBorderColor(INDIGO);
        c.setPadding(6);
        return c;
    }

    private static PdfPCell body(String text, boolean center) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c.setBorderColor(new Color(226, 232, 240));
        c.setPadding(5);
        if (center) c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    /** Horizontal stacked bar: positive / neutral / negative proportions. */
    private static PdfPTable sentimentBar(Counts c) {
        List<Float> widths = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        if (c.pos > 0) { widths.add((float) c.pos); colors.add(GREEN); }
        if (c.neu > 0) { widths.add((float) c.neu); colors.add(GREY); }
        if (c.neg > 0) { widths.add((float) c.neg); colors.add(RED); }
        float[] w = new float[widths.size()];
        for (int i = 0; i < w.length; i++) w[i] = widths.get(i);
        PdfPTable bar = new PdfPTable(w.length);
        try {
            bar.setWidths(w);
        } catch (Exception ignored) { }
        bar.setWidthPercentage(100);
        for (Color col : colors) {
            PdfPCell cell = new PdfPCell(new Phrase(" "));
            cell.setBackgroundColor(col);
            cell.setBorder(0);
            cell.setFixedHeight(12);
            bar.addCell(cell);
        }
        return bar;
    }

    private static String label(Review r) {
        return r.getSentimentResult() == null ? null : r.getSentimentResult().getSentimentLabel();
    }

    private static String pct(int part, int total) {
        return total == 0 ? "0%" : String.format("%.0f%%", part * 100.0 / total);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static class Counts {
        int total, pos, neg, neu, ratingN;
        long ratingSum;

        void add(Review r) {
            total++;
            String l = label(r);
            if ("POSITIVE".equals(l)) pos++;
            else if ("NEGATIVE".equals(l)) neg++;
            else if ("NEUTRAL".equals(l)) neu++;
            if (r.getRating() != null) { ratingSum += r.getRating(); ratingN++; }
        }

        double avgRating() { return ratingN == 0 ? 0 : (double) ratingSum / ratingN; }

        String overall() {
            if (pos + neg + neu == 0) return "NO DATA";
            if (pos > neg) return "POSITIVE";
            if (neg > pos) return "NEGATIVE";
            return "NEUTRAL";
        }
    }
}