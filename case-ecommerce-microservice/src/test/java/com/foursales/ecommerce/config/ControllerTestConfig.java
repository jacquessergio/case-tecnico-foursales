package com.foursales.ecommerce.config;

import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.enums.UserRole;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

@TestConfiguration
public class ControllerTestConfig {

    @Bean
    @Primary
    public UserDetailsService testUserDetailsService() {
        return username -> {
            if ("test@test.com".equals(username)) {
                User user = new User("Test User", "test@test.com", "password", UserRole.USER);
                user.setId(UUID.randomUUID());
                return user;
            } else if ("admin@test.com".equals(username)) {
                User user = new User("Admin User", "admin@test.com", "password", UserRole.ADMIN);
                user.setId(UUID.randomUUID());
                return user;
            }
            throw new UsernameNotFoundException("User not found: " + username);
        };
    }
}