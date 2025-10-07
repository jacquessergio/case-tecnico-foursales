package com.foursales.ecommerce.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.ecommerce.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Filter that applies rate limiting to incoming HTTP requests
 * Runs before authentication to protect against brute force attacks
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitKeyResolver keyResolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip rate limiting for actuator endpoints, swagger, and static resources
        if (shouldSkipRateLimiting(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine rate limit type based on endpoint
        RateLimitType rateLimitType = keyResolver.determineRateLimitType(requestPath);

        // Resolve client key (IP or user-based)
        String clientKey = resolveClientKey(request, rateLimitType);

        // Try to consume a token from the bucket
        boolean allowed = rateLimitService.tryConsume(clientKey, rateLimitType);

        if (allowed) {
            // Add rate limit headers to response
            addRateLimitHeaders(response, clientKey, rateLimitType);
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded - reject request
            handleRateLimitExceeded(request, response, rateLimitType);
        }
    }

    /**
     * Resolves the appropriate client key based on rate limit type
     */
    private String resolveClientKey(HttpServletRequest request, RateLimitType rateLimitType) {
        return switch (rateLimitType) {
            case AUTH, PUBLIC, SEARCH -> keyResolver.resolveIpKey(request);
            case USER, ADMIN, REPORT -> {
                String userKey = keyResolver.resolveUserKey(request);
                yield userKey != null ? userKey : keyResolver.resolveIpKey(request);
            }
        };
    }

    /**
     * Adds rate limit information to response headers
     */
    private void addRateLimitHeaders(HttpServletResponse response, String clientKey, RateLimitType rateLimitType) {
        long remainingTokens = rateLimitService.getRemainingTokens(clientKey, rateLimitType);

        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitType.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remainingTokens));
        response.setHeader("X-RateLimit-Reset", String.valueOf(
                System.currentTimeMillis() + rateLimitType.getRefillDuration().toMillis()));
    }

    /**
     * Handles rate limit exceeded scenario
     */
    private void handleRateLimitExceeded(
            HttpServletRequest request,
            HttpServletResponse response,
            RateLimitType rateLimitType) throws IOException {

        log.warn("Rate limit exceeded for {} - Path: {}, Type: {}",
                keyResolver.resolveKey(request),
                request.getRequestURI(),
                rateLimitType);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .code(HttpStatus.TOO_MANY_REQUESTS.value())
                .message("Rate limit exceeded")
                .stackTrace(String.format(
                        "Too many requests. Limit: %d requests per %d seconds. Please try again later.",
                        rateLimitType.getCapacity(),
                        rateLimitType.getRefillDuration().getSeconds()))
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        // Add Retry-After header
        response.setHeader("Retry-After", String.valueOf(rateLimitType.getRefillDuration().getSeconds()));

        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitType.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset", String.valueOf(
                System.currentTimeMillis() + rateLimitType.getRefillDuration().toMillis()));

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Determines if rate limiting should be skipped for this path
     */
    private boolean shouldSkipRateLimiting(String requestPath) {
        return requestPath.startsWith("/actuator") ||
                requestPath.startsWith("/swagger-ui") ||
                requestPath.startsWith("/v3/api-docs") ||
                requestPath.startsWith("/error") ||
                requestPath.endsWith(".css") ||
                requestPath.endsWith(".js") ||
                requestPath.endsWith(".html") ||
                requestPath.endsWith(".ico");
    }
}
