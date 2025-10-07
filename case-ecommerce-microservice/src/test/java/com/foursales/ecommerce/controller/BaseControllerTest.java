package com.foursales.ecommerce.controller;

import com.foursales.ecommerce.config.ControllerTestConfig;
import com.foursales.ecommerce.exception.ErrorResponseBuilder;
import com.foursales.ecommerce.ratelimit.RateLimitKeyResolver;
import com.foursales.ecommerce.ratelimit.RateLimitService;
import com.foursales.ecommerce.ratelimit.RateLimitType;
import com.foursales.ecommerce.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

@Import(ControllerTestConfig.class)
public abstract class BaseControllerTest {

    @MockBean
    protected ErrorResponseBuilder errorResponseBuilder;

    @MockBean
    protected RateLimitService rateLimitService;

    @MockBean
    protected RateLimitKeyResolver rateLimitKeyResolver;

    @MockBean
    protected JwtTokenProvider jwtTokenProvider;

    @MockBean
    protected UserDetailsService userDetailsService;

    @BeforeEach
    void setUpBase() throws Exception {
        // Configure rate limiting mocks
        lenient().when(rateLimitKeyResolver.determineRateLimitType(anyString()))
                .thenReturn(RateLimitType.PUBLIC);
        lenient().when(rateLimitKeyResolver.resolveKey(any())).thenReturn("test-key");
        lenient().when(rateLimitKeyResolver.resolveIpKey(any())).thenReturn("127.0.0.1");
        lenient().when(rateLimitKeyResolver.resolveUserKey(any())).thenReturn("test-user");
        lenient().when(rateLimitService.tryConsume(anyString(), any())).thenReturn(true);
        lenient().when(rateLimitService.getRemainingTokens(anyString(), any())).thenReturn(100L);

        // Configure JWT mocks
        lenient().when(jwtTokenProvider.getUsernameFromToken(anyString())).thenReturn("test@test.com");
        lenient().when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);

        // UserDetailsService is configured via ControllerTestConfig

        setUpChild();
    }

    protected abstract void setUpChild() throws Exception;
}