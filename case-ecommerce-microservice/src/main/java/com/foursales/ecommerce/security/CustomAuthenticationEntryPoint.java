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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        @Override
        public void commence(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {

                log.warn("Unauthorized access attempt on path: {} - Reason: {}",
                                request.getRequestURI(),
                                authException.getMessage());

                ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                                .code(HttpStatus.UNAUTHORIZED.value())
                                .message("Not authenticated")
                                .stackTrace("Invalid, expired or missing JWT token")
                                .path(request.getRequestURI())
                                .build();

                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }
}
