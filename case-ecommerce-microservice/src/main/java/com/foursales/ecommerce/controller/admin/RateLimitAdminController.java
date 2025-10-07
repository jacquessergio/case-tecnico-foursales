package com.foursales.ecommerce.controller.admin;

import com.foursales.ecommerce.ratelimit.RateLimitService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/rate-limit")
@RequiredArgsConstructor
@Tag(name = "Admin - Rate Limit", description = "Rate limiting monitoring and statistics")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Hidden
public class RateLimitAdminController {

    private final RateLimitService rateLimitService;

    @Operation(summary = "Get rate limiting statistics")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getRateLimitStats() {
        Map<String, Object> stats = rateLimitService.getRateLimitStats();
        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "Clear bucket for a specific user")
    @DeleteMapping("/bucket/{key}")
    public ResponseEntity<Map<String, String>> clearBucket(@PathVariable String key) {
        rateLimitService.clearBucket(key);

        log.info("Admin cleared rate limit bucket for key: {}", key);

        return ResponseEntity.ok(Map.of(
                "message", "Rate limit bucket cleared successfully",
                "key", key));
    }

    @Operation(summary = "Get information for a specific bucket")
    @GetMapping("/bucket/{key}")
    public ResponseEntity<Map<String, Object>> getBucketInfo(@PathVariable String key) {
        Map<String, Object> info = rateLimitService.getBucketInfo(key);
        return ResponseEntity.ok(info);
    }
}
