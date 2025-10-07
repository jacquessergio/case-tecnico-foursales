package com.foursales.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LoginAttemptServiceTest {

    private LoginAttemptService loginAttemptService;
    private static final String TEST_KEY = "test@test.com";

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService();
    }

    @Test
    @DisplayName("Should return false when account is not blocked")
    void shouldReturnFalseWhenAccountIsNotBlocked() {
        boolean isBlocked = loginAttemptService.isBlocked(TEST_KEY);

        assertThat(isBlocked).isFalse();
    }

    @Test
    @DisplayName("Should increment failed attempts on login failure")
    void shouldIncrementFailedAttemptsOnLoginFailure() {
        loginAttemptService.loginFailed(TEST_KEY);

        int attempts = loginAttemptService.getAttempts(TEST_KEY);

        assertThat(attempts).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track multiple failed attempts")
    void shouldTrackMultipleFailedAttempts() {
        loginAttemptService.loginFailed(TEST_KEY);
        loginAttemptService.loginFailed(TEST_KEY);
        loginAttemptService.loginFailed(TEST_KEY);

        int attempts = loginAttemptService.getAttempts(TEST_KEY);

        assertThat(attempts).isEqualTo(3);
    }

    @Test
    @DisplayName("Should block account after 5 failed attempts")
    void shouldBlockAccountAfter5FailedAttempts() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.loginFailed(TEST_KEY);
        }

        boolean isBlocked = loginAttemptService.isBlocked(TEST_KEY);

        assertThat(isBlocked).isTrue();
    }

    @Test
    @DisplayName("Should clear failed attempts on successful login")
    void shouldClearFailedAttemptsOnSuccessfulLogin() {
        loginAttemptService.loginFailed(TEST_KEY);
        loginAttemptService.loginFailed(TEST_KEY);

        loginAttemptService.loginSucceeded(TEST_KEY);

        int attempts = loginAttemptService.getAttempts(TEST_KEY);

        assertThat(attempts).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return correct remaining attempts")
    void shouldReturnCorrectRemainingAttempts() {
        loginAttemptService.loginFailed(TEST_KEY);
        loginAttemptService.loginFailed(TEST_KEY);

        int remainingAttempts = loginAttemptService.getRemainingAttempts(TEST_KEY);

        assertThat(remainingAttempts).isEqualTo(3);
    }

    @Test
    @DisplayName("Should return 0 remaining attempts when blocked")
    void shouldReturn0RemainingAttemptsWhenBlocked() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.loginFailed(TEST_KEY);
        }

        int remainingAttempts = loginAttemptService.getRemainingAttempts(TEST_KEY);

        assertThat(remainingAttempts).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return 0 remaining attempts when attempts exceed max")
    void shouldReturn0RemainingAttemptsWhenAttemptsExceedMax() {
        for (int i = 0; i < 10; i++) {
            loginAttemptService.loginFailed(TEST_KEY);
        }

        int remainingAttempts = loginAttemptService.getRemainingAttempts(TEST_KEY);

        assertThat(remainingAttempts).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return 5 remaining attempts for new account")
    void shouldReturn5RemainingAttemptsForNewAccount() {
        int remainingAttempts = loginAttemptService.getRemainingAttempts(TEST_KEY);

        assertThat(remainingAttempts).isEqualTo(5);
    }

    @Test
    @DisplayName("Should return lockout duration as 30 minutes")
    void shouldReturnLockoutDurationAs30Minutes() {
        int lockoutDuration = loginAttemptService.getLockoutDurationMinutes();

        assertThat(lockoutDuration).isEqualTo(30);
    }

    @Test
    @DisplayName("Should handle multiple different accounts independently")
    void shouldHandleMultipleDifferentAccountsIndependently() {
        String key1 = "user1@test.com";
        String key2 = "user2@test.com";

        loginAttemptService.loginFailed(key1);
        loginAttemptService.loginFailed(key1);

        loginAttemptService.loginFailed(key2);

        assertThat(loginAttemptService.getAttempts(key1)).isEqualTo(2);
        assertThat(loginAttemptService.getAttempts(key2)).isEqualTo(1);
    }

    @Test
    @DisplayName("Should not block account after successful login clears attempts")
    void shouldNotBlockAccountAfterSuccessfulLoginClearsAttempts() {
        for (int i = 0; i < 4; i++) {
            loginAttemptService.loginFailed(TEST_KEY);
        }

        loginAttemptService.loginSucceeded(TEST_KEY);

        boolean isBlocked = loginAttemptService.isBlocked(TEST_KEY);

        assertThat(isBlocked).isFalse();
    }

    @Test
    @DisplayName("Should return 0 attempts for unknown key")
    void shouldReturn0AttemptsForUnknownKey() {
        int attempts = loginAttemptService.getAttempts("unknown@test.com");

        assertThat(attempts).isEqualTo(0);
    }
}
