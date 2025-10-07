package com.foursales.ecommerce.service;

import com.foursales.ecommerce.dto.JwtResponse;
import com.foursales.ecommerce.dto.LoginRequest;
import com.foursales.ecommerce.dto.RegisterRequest;
import com.foursales.ecommerce.dto.RegisterResponse;
import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.exception.AccountLockedException;
import com.foursales.ecommerce.exception.BusinessException;
import com.foursales.ecommerce.repository.jpa.UserRepository;
import com.foursales.ecommerce.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttemptService loginAttemptService;

    // SECURITY FIX: Account lockout prevents brute force attacks
    @Override
    public JwtResponse login(LoginRequest loginRequest) {
        String email = loginRequest.getEmail();

        if (loginAttemptService.isBlocked(email)) {
            int attempts = loginAttemptService.getAttempts(email);
            int lockoutMinutes = loginAttemptService.getLockoutDurationMinutes();
            throw new AccountLockedException(
                    String.format("Account temporarily locked due to %d failed login attempts. " +
                            "Try again in %d minutes.",
                            attempts, lockoutMinutes));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            loginRequest.getPassword()));

            loginAttemptService.loginSucceeded(email);

            String jwt = tokenProvider.generateToken(authentication);
            User user = (User) authentication.getPrincipal();

            log.info("Successful login for user: {}", email);

            return new JwtResponse(jwt, user.getEmail(), user.getRole().name());

        } catch (BadCredentialsException e) {
            loginAttemptService.loginFailed(email);

            int remainingAttempts = loginAttemptService.getRemainingAttempts(email);
            if (remainingAttempts > 0) {
                throw new BadCredentialsException(
                        String.format("Invalid credentials. %d attempt(s) remaining before lockout.",
                                remainingAttempts));
            } else {
                throw new AccountLockedException(
                        String.format("Account locked due to too many failed attempts. " +
                                "Try again in %d minutes.",
                                loginAttemptService.getLockoutDurationMinutes()));
            }
        }
    }

    @Override
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BusinessException("Email is already in use!");
        }

        User user = new User(
                registerRequest.getName(),
                registerRequest.getEmail(),
                passwordEncoder.encode(registerRequest.getPassword()),
                registerRequest.getRole());

        userRepository.save(user);

        return new RegisterResponse("User registered successfully!");
    }
}
