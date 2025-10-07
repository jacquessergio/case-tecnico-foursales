package com.foursales.ecommerce.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.ecommerce.dto.ApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom Access Denied Handler
 *
 * Handles 403 Forbidden responses when authenticated user lacks required
 * role/authority.
 * Returns ApiErrorResponse JSON instead of default Spring Security HTML error
 * page.
 *
 * Flow:
 * 1. User authenticated (valid JWT)
 * 2. User tries to access endpoint requiring ADMIN role
 * 3. User only has USER role
 * 4. Spring Security throws AccessDeniedException
 * 5. This handler catches it and returns JSON response matching Swagger schema
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

        private final ObjectMapper objectMapper;

        @Override
        public void handle(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException, ServletException {

                log.warn("Access denied for user on path: {} - Reason: {}",
                                request.getRequestURI(),
                                accessDeniedException.getMessage());

                ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                                .code(HttpStatus.FORBIDDEN.value())
                                .message("Access denied")
                                .stackTrace("You do not have permission to access this resource")
                                .path(request.getRequestURI())
                                .build();

                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }
}
