package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.admin.AnalyticsResponse;
import com.exploreceylon.backend.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Falls under SecurityConfig's "/api/v1/admin/**" -> hasRole("ADMIN") rule.
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    // GET /api/v1/admin/analytics
    @GetMapping
    public ResponseEntity<AnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(adminAnalyticsService.getAnalytics());
    }
}
