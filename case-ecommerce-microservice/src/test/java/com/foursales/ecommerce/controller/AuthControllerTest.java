package com.foursales.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.ecommerce.config.TestConfig;
import com.foursales.ecommerce.dto.JwtResponse;
import com.foursales.ecommerce.dto.LoginRequest;
import com.foursales.ecommerce.dto.RegisterRequest;
import com.foursales.ecommerce.dto.RegisterResponse;
import com.foursales.ecommerce.enums.UserRole;
import com.foursales.ecommerce.exception.AccountLockedException;
import com.foursales.ecommerce.exception.BusinessException;
import com.foursales.ecommerce.service.IAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.elasticsearch.uris=",
    "spring.data.elasticsearch.repositories.enabled=false",
    "management.health.elasticsearch.enabled=false"
})
@Import(TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IAuthService authService;

    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;
    private JwtResponse jwtResponse;
    private RegisterResponse registerResponse;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest("test@test.com", "Password@123");
        registerRequest = new RegisterRequest("Test User", "test@test.com", "Password@123", UserRole.USER);
        jwtResponse = new JwtResponse("jwt-token", "test@test.com", "USER");
        registerResponse = new RegisterResponse("User registered successfully!");
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(jwtResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("Should return 401 when login with invalid credentials")
    void shouldReturn401WhenLoginWithInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("Should return 423 when account is locked")
    void shouldReturn423WhenAccountIsLocked() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountLockedException("Account locked"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isLocked());

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("Should return 400 when login with invalid request body")
    void shouldReturn400WhenLoginWithInvalidRequestBody() throws Exception {
        LoginRequest invalidRequest = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("Should register user successfully")
    void shouldRegisterUserSuccessfully() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(registerResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("Should return 400 when registering with existing email")
    void shouldReturn400WhenRegisteringWithExistingEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException("Email is already in use!"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("Should return 400 when register with invalid request body")
    void shouldReturn400WhenRegisterWithInvalidRequestBody() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest("", "", "", UserRole.USER);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("Should return 400 when register with weak password")
    void shouldReturn400WhenRegisterWithWeakPassword() throws Exception {
        RegisterRequest weakPasswordRequest = new RegisterRequest("Test User", "test@test.com", "weak", UserRole.USER);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakPasswordRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }
}
