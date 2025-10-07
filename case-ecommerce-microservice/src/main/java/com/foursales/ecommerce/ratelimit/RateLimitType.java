package com.foursales.ecommerce.ratelimit;

import io.github.bucket4j.Bandwidth;

import java.time.Duration;

/**
 * Defines different rate limit configurations for various endpoint types
 */
public enum RateLimitType {

    /**
     * Public endpoints without authentication (product listing, search)
     * Limit: 100 requests per minute per IP
     */
    PUBLIC(100, Duration.ofMinutes(1)),

    /**
     * Authentication endpoints (login, register)
     * Limit: 5 requests per minute per IP (prevents brute force)
     */
    AUTH(5, Duration.ofMinutes(1)),

    /**
     * Authenticated user endpoints (create order, payment)
     * Limit: 30 requests per minute per user
     */
    USER(30, Duration.ofMinutes(1)),

    /**
     * Admin endpoints (product management, reports)
     * Limit: 60 requests per minute per admin user
     */
    ADMIN(60, Duration.ofMinutes(1)),

    /**
     * Search endpoints (Elasticsearch queries - expensive)
     * Limit: 60 requests per minute per IP
     */
    SEARCH(60, Duration.ofMinutes(1)),

    /**
     * Report endpoints (database-intensive queries)
     * Limit: 10 requests per minute per user
     */
    REPORT(10, Duration.ofMinutes(1));

    private final int capacity;
    private final Duration refillDuration;

    RateLimitType(int capacity, Duration refillDuration) {
        this.capacity = capacity;
        this.refillDuration = refillDuration;
    }

    /**
     * Creates a Bandwidth configuration for this rate limit type
     * Uses Bucket4j 8.7.0 builder API with greedy refill for gradual token recovery
     *
     * Algorithm: Tokens refill continuously over time (not all at once)
     * Example (AUTH: 5 tokens/60s):
     * - After 12s → 1 token refills
     * - After 24s → 2 tokens refill
     * - After 60s → all 5 tokens refilled
     *
     * @return Bandwidth configuration with capacity and greedy refill strategy
     */
    public Bandwidth toBandwidth() {
        return Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();
    }

    public int getCapacity() {
        return capacity;
    }

    public Duration getRefillDuration() {
        return refillDuration;
    }
}
