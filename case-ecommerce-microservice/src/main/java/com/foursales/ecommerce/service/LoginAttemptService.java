package com.foursales.ecommerce.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Login Attempt Tracking Service
 *
 * Implements account lockout mechanism to prevent brute force attacks.
 *
 * Security Features:
 * - Max 5 failed attempts before lockout
 * - 30 minute lockout duration
 * - Automatic unlock after timeout
 * - In-memory cache (Caffeine) for performance
 *
 * Note: For production with multiple instances, consider Redis for distributed
 * tracking
 */
@Service
@Slf4j
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    private final Cache<String, Integer> attemptsCache;

    public LoginAttemptService() {
        this.attemptsCache = Caffeine.newBuilder()
                .expireAfterWrite(LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    /**
     * Called after successful login - clears failed attempts
     */
    public void loginSucceeded(String key) {
        attemptsCache.invalidate(key);
        log.debug(" Login succeeded for {}. Failed attempts cleared.", key);
    }

    /**
     * Called after failed login - increments failed attempts counter
     */
    public void loginFailed(String key) {
        int attempts = attemptsCache.asMap().getOrDefault(key, 0) + 1;
        attemptsCache.put(key, attempts);

        log.warn(" Failed login attempt {} for {}", attempts, key);

        if (attempts >= MAX_ATTEMPTS) {
            log.error("Account locked due to {} failed attempts: {}", MAX_ATTEMPTS, key);
            // TODO: Integrate with alerting system
            // alertingService.sendAlert("Account lockout", key);
        }
    }

    /**
     * Checks if account is currently locked due to too many failed attempts
     */
    public boolean isBlocked(String key) {
        return attemptsCache.asMap().getOrDefault(key, 0) >= MAX_ATTEMPTS;
    }

    /**
     * Returns number of failed attempts for given key
     */
    public int getAttempts(String key) {
        return attemptsCache.asMap().getOrDefault(key, 0);
    }

    /**
     * Returns number of remaining attempts before lockout
     */
    public int getRemainingAttempts(String key) {
        int attempts = getAttempts(key);
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }

    /**
     * Gets lockout duration in minutes
     */
    public int getLockoutDurationMinutes() {
        return LOCKOUT_DURATION_MINUTES;
    }
}
