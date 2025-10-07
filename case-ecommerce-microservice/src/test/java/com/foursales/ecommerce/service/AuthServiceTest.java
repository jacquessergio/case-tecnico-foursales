package com.foursales.ecommerce.service;

import com.foursales.ecommerce.dto.JwtResponse;
import com.foursales.ecommerce.dto.LoginRequest;
import com.foursales.ecommerce.dto.RegisterRequest;
import com.foursales.ecommerce.dto.RegisterResponse;
import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.enums.UserRole;
import com.foursales.ecommerce.exception.AccountLockedException;
import com.foursales.ecommerce.exception.BusinessException;
import com.foursales.ecommerce.repository.jpa.UserRepository;
import com.foursales.ecommerce.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthService authService;

    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;
    private User user;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest("test@test.com", "password123");
        registerRequest = new RegisterRequest("Test User", "test@test.com", "Password@123", UserRole.USER);
        user = new User("Test User", "test@test.com", "encodedPassword", UserRole.USER);
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token");

        JwtResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getRole()).isEqualTo("USER");

        verify(loginAttemptService).loginSucceeded("test@test.com");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Should throw AccountLockedException when account is blocked")
    void shouldThrowAccountLockedExceptionWhenBlocked() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(true);
        when(loginAttemptService.getAttempts(anyString())).thenReturn(5);
        when(loginAttemptService.getLockoutDurationMinutes()).thenReturn(30);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("Account temporarily locked");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException with remaining attempts")
    void shouldThrowBadCredentialsExceptionWithRemainingAttempts() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));
        when(loginAttemptService.getRemainingAttempts(anyString())).thenReturn(3);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("3 attempt(s) remaining");

        verify(loginAttemptService).loginFailed("test@test.com");
    }

    @Test
    @DisplayName("Should throw AccountLockedException when no attempts remaining")
    void shouldThrowAccountLockedExceptionWhenNoAttemptsRemaining() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));
        when(loginAttemptService.getRemainingAttempts(anyString())).thenReturn(0);
        when(loginAttemptService.getLockoutDurationMinutes()).thenReturn(30);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("Account locked");

        verify(loginAttemptService).loginFailed("test@test.com");
    }

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUserSuccessfully() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        RegisterResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("User registered successfully!");

        verify(userRepository).existsByEmail("test@test.com");
        verify(passwordEncoder).encode("Password@123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when email already exists")
    void shouldThrowBusinessExceptionWhenEmailExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email is already in use!");

        verify(userRepository, never()).save(any(User.class));
    }
}
