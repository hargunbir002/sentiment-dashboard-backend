package com.sentimentdashboard.sentiment_dashboard.controller;

import com.sentimentdashboard.sentiment_dashboard.entity.Review;
import com.sentimentdashboard.sentiment_dashboard.repository.ReviewRepository;
import com.sentimentdashboard.sentiment_dashboard.service.AspectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/analytics/aspects                -> aspect sentiment across all reviews
 * GET /api/analytics/aspects?productId=1    -> aspect sentiment for one product
 */
@RestController
@RequestMapping("/api/analytics/aspects")
public class AspectController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AspectService aspectService;

    @GetMapping
    public List<AspectService.AspectStat> getAspects(@RequestParam(required = false) Long productId) {
        List<Review> reviews = productId == null
                ? reviewRepository.findAll()
                : reviewRepository.findByProductId(productId);
        return aspectService.analyze(reviews);
    }
}