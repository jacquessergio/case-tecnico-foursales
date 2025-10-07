package com.foursales.ecommerce.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service responsible for managing rate limit buckets
 * Uses Caffeine cache to store buckets per client key
 */
@Service
@Slf4j
public class RateLimitService {

    private final LoadingCache<String, Bucket> buckets;

    public RateLimitService() {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(100_000) // Max 100k concurrent clients
                .expireAfterAccess(Duration.ofMinutes(10)) // Remove inactive buckets after 10 minutes
                .build(this::createNewBucket);
    }

    /**
     * Tries to consume a token from the bucket for the given key
     *
     * @param key           Unique identifier for the client (IP, user, etc.)
     * @param rateLimitType Type of rate limit to apply
     * @return true if token consumed successfully, false if rate limit exceeded
     */
    public boolean tryConsume(String key, RateLimitType rateLimitType) {
        // Pass rateLimitType to createNewBucket via key prefix
        String bucketKey = rateLimitType.name() + ":" + key;
        Bucket bucket = buckets.get(bucketKey);

        boolean consumed = bucket.tryConsume(1);

        if (!consumed) {
            log.warn("Rate limit exceeded for key: {} (type: {})", key, rateLimitType);
        }

        return consumed;
    }

    /**
     * Gets remaining tokens in the bucket
     *
     * @param key           Unique identifier for the client
     * @param rateLimitType Type of rate limit
     * @return Number of available tokens
     */
    public long getRemainingTokens(String key, RateLimitType rateLimitType) {
        String bucketKey = rateLimitType.name() + ":" + key;
        Bucket bucket = buckets.get(bucketKey);
        return bucket.getAvailableTokens();
    }

    /**
     * Creates a new bucket with the specified rate limit configuration
     *
     * @param bucketKey Bucket key (format: "RateLimitType:clientKey")
     * @return New Bucket instance
     */
    private Bucket createNewBucket(String bucketKey) {
        // Extract rate limit type from key prefix (before first colon)
        int colonIndex = bucketKey.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid bucket key format: " + bucketKey);
        }

        String rateLimitTypeName = bucketKey.substring(0, colonIndex);
        RateLimitType rateLimitType = RateLimitType.valueOf(rateLimitTypeName);

        log.debug("Creating new bucket for key: {} with type: {}", bucketKey, rateLimitType);

        return Bucket.builder()
                .addLimit(rateLimitType.toBandwidth())
                .build();
    }

    /**
     * Clears all buckets (useful for testing or admin operations)
     */
    public void clearAllBuckets() {
        buckets.invalidateAll();
        log.info("All rate limit buckets cleared");
    }

    /**
     * Gets detailed rate limit statistics for admin monitoring
     */
    public java.util.Map<String, Object> getRateLimitStats() {
        var stats = buckets.stats();
        return java.util.Map.of(
                "cacheSize", buckets.estimatedSize(),
                "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
                "missRate", String.format("%.2f%%", stats.missRate() * 100),
                "loadSuccessCount", stats.loadSuccessCount(),
                "loadFailureCount", stats.loadFailureCount(),
                "evictionCount", stats.evictionCount());
    }

    /**
     * Clears a specific bucket (admin operation)
     */
    public void clearBucket(String key) {
        buckets.invalidate(key);
        log.info("Rate limit bucket cleared for key: {}", key);
    }

    /**
     * Gets information about a specific bucket
     */
    public java.util.Map<String, Object> getBucketInfo(String key) {
        Bucket bucket = buckets.getIfPresent(key);
        if (bucket == null) {
            return java.util.Map.of("exists", false, "key", key);
        }

        return java.util.Map.of(
                "exists", true,
                "key", key,
                "availableTokens", bucket.getAvailableTokens());
    }
}
