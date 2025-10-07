package com.foursales.ecommerce.controller;

import com.foursales.ecommerce.dto.MonthlyRevenueResponse;
import com.foursales.ecommerce.dto.TopUserResponse;
import com.foursales.ecommerce.dto.UserAverageTicketResponse;
import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.config.TestConfig;
import com.foursales.ecommerce.enums.UserRole;
import com.foursales.ecommerce.repository.jpa.UserRepository;
import com.foursales.ecommerce.security.JwtTokenProvider;
import com.foursales.ecommerce.service.IReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IReportService reportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private TopUserResponse topUserResponse;
    private UserAverageTicketResponse averageTicketResponse;
    private MonthlyRevenueResponse monthlyRevenueResponse;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        User adminUser = new User("Admin User", "admin@test.com", passwordEncoder.encode("password"), UserRole.ADMIN);
        adminUser = userRepository.save(adminUser);
        org.springframework.security.core.Authentication adminAuth =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                adminUser, null, adminUser.getAuthorities());
        adminToken = jwtTokenProvider.generateToken(adminAuth);

        User regularUser = new User("Regular User", "user@test.com", passwordEncoder.encode("password"), UserRole.USER);
        regularUser = userRepository.save(regularUser);
        org.springframework.security.core.Authentication userAuth =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                regularUser, null, regularUser.getAuthorities());
        userToken = jwtTokenProvider.generateToken(userAuth);

        UUID userId = UUID.randomUUID();

        topUserResponse = new TopUserResponse(userId, "Test User", "test@test.com", 10);

        averageTicketResponse = new UserAverageTicketResponse(
                userId,
                "Test User",
                "test@test.com",
                new BigDecimal("250.50")
        );

        monthlyRevenueResponse = new MonthlyRevenueResponse(
                "JANUARY",
                2025,
                new BigDecimal("15000.00"),
                50L,
                new BigDecimal("300.00"),
                LocalDateTime.now().withDayOfMonth(1),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Should get top users as admin")
    void shouldGetTopUsersAsAdmin() throws Exception {
        when(reportService.getTopUsers(any(), any())).thenReturn(List.of(topUserResponse));

        mockMvc.perform(get("/api/v1/reports/top-users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].name").value("Test User"))
                .andExpect(jsonPath("$.[0].orderCount").value(10));

        verify(reportService).getTopUsers(any(), any());
    }

    @Test
    @DisplayName("Should get top users with date filters")
    void shouldGetTopUsersWithDateFilters() throws Exception {
        when(reportService.getTopUsers(any(), any())).thenReturn(List.of(topUserResponse));

        mockMvc.perform(get("/api/v1/reports/top-users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].name").value("Test User"));

        verify(reportService).getTopUsers(any(), any());
    }

    @Test
    @DisplayName("Should return 403 when non-admin tries to get top users")
    void shouldReturn403WhenNonAdminTriesToGetTopUsers() throws Exception {
        mockMvc.perform(get("/api/v1/reports/top-users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        verify(reportService, never()).getTopUsers(any(), any());
    }

    @Test
    @DisplayName("Should get average ticket by user as admin")
    void shouldGetAverageTicketByUserAsAdmin() throws Exception {
        when(reportService.getAverageTicketByUser(any(), any()))
                .thenReturn(List.of(averageTicketResponse));

        mockMvc.perform(get("/api/v1/reports/average-ticket")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].name").value("Test User"))
                .andExpect(jsonPath("$.[0].averageTicket").value(250.50));

        verify(reportService).getAverageTicketByUser(any(), any());
    }

    @Test
    @DisplayName("Should get average ticket with date filters")
    void shouldGetAverageTicketWithDateFilters() throws Exception {
        when(reportService.getAverageTicketByUser(any(), any()))
                .thenReturn(List.of(averageTicketResponse));

        mockMvc.perform(get("/api/v1/reports/average-ticket")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].averageTicket").value(250.50));

        verify(reportService).getAverageTicketByUser(any(), any());
    }

    @Test
    @DisplayName("Should return 403 when non-admin tries to get average ticket")
    void shouldReturn403WhenNonAdminTriesToGetAverageTicket() throws Exception {
        mockMvc.perform(get("/api/v1/reports/average-ticket")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        verify(reportService, never()).getAverageTicketByUser(any(), any());
    }

    @Test
    @DisplayName("Should get current month revenue as admin")
    void shouldGetCurrentMonthRevenueAsAdmin() throws Exception {
        when(reportService.getCurrentMonthRevenue()).thenReturn(monthlyRevenueResponse);

        mockMvc.perform(get("/api/v1/reports/current-month-revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(15000.00))
                .andExpect(jsonPath("$.totalOrders").value(50));

        verify(reportService).getCurrentMonthRevenue();
    }

    @Test
    @DisplayName("Should return 403 when non-admin tries to get current month revenue")
    void shouldReturn403WhenNonAdminTriesToGetCurrentMonthRevenue() throws Exception {
        mockMvc.perform(get("/api/v1/reports/current-month-revenue")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        verify(reportService, never()).getCurrentMonthRevenue();
    }

    @Test
    @DisplayName("Should return 401 when accessing reports without authentication")
    void shouldReturn401WhenAccessingReportsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/reports/top-users"))
                .andExpect(status().isUnauthorized());

        verify(reportService, never()).getTopUsers(any(), any());
    }
}
