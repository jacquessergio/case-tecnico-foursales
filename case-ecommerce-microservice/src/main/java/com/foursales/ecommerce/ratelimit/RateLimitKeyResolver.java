package com.foursales.ecommerce.ratelimit;

import com.foursales.ecommerce.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the key to be used for rate limiting based on request context
 */
@Component
@Slf4j
public class RateLimitKeyResolver {

    /**
     * Resolves key based on authenticated user (if available) or client IP
     *
     * @param request HTTP request
     * @return Unique key for rate limiting
     */
    public String resolveKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            return "user:" + user.getId().toString();
        }

        return "ip:" + getClientIp(request);
    }

    /**
     * Resolves key specifically for IP-based limiting (e.g., public endpoints)
     *
     * @param request HTTP request
     * @return IP-based key
     */
    public String resolveIpKey(HttpServletRequest request) {
        return "ip:" + getClientIp(request);
    }

    /**
     * Resolves key for user-based limiting
     *
     * @param request HTTP request
     * @return User-based key or null if not authenticated
     */
    public String resolveUserKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            return "user:" + user.getId().toString();
        }

        return null;
    }

    /**
     * Extracts client IP address from request, considering proxy headers
     *
     * @param request HTTP request
     * @return Client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // If X-Forwarded-For contains multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }

    /**
     * Determines the appropriate rate limit type based on the request path
     *
     * @param requestPath Request URI path
     * @return Appropriate RateLimitType
     */
    public RateLimitType determineRateLimitType(String requestPath) {
        if (requestPath.startsWith("/api/v1/auth/")) {
            return RateLimitType.AUTH;
        } else if (requestPath.startsWith("/api/v1/reports/")) {
            return RateLimitType.REPORT;
        } else if (requestPath.contains("/search")) {
            return RateLimitType.SEARCH;
        } else if (requestPath.startsWith("/api/v1/products") && isAdminOperation(requestPath)) {
            return RateLimitType.ADMIN;
        } else if (requestPath.startsWith("/api/v1/orders")) {
            return RateLimitType.USER;
        } else {
            return RateLimitType.PUBLIC;
        }
    }

    /**
     * Checks if the request is an admin operation (POST, PUT, DELETE)
     */
    private boolean isAdminOperation(String requestPath) {
        // This is a simple heuristic; in a real system, you'd check the HTTP method
        // and possibly the user's role from the security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            return user.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
}
