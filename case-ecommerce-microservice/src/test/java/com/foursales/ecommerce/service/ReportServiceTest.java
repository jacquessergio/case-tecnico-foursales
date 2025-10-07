package com.foursales.ecommerce.service;

import com.foursales.ecommerce.dto.MonthlyRevenueResponse;
import com.foursales.ecommerce.dto.TopUserResponse;
import com.foursales.ecommerce.dto.UserAverageTicketResponse;
import com.foursales.ecommerce.repository.jpa.OrderRepository;
import com.foursales.ecommerce.repository.jpa.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ReportService reportService;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @BeforeEach
    void setUp() {
        startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
        endDate = LocalDateTime.of(2025, 1, 31, 23, 59);
    }

    @Test
    @DisplayName("Should get top users with date filter")
    void shouldGetTopUsersWithDateFilter() {
        TopUserResponse topUser = new TopUserResponse(UUID.randomUUID(), "Test User", "user@test.com", 10);
        when(userRepository.findTopUsersByOrderCountOptimized(any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(topUser));

        List<TopUserResponse> result = reportService.getTopUsers(startDate, endDate);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("user@test.com");
        assertThat(result.get(0).getOrderCount()).isEqualTo(10);

        verify(userRepository).findTopUsersByOrderCountOptimized(eq(startDate), eq(endDate), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should get top users without date filter")
    void shouldGetTopUsersWithoutDateFilter() {
        TopUserResponse topUser = new TopUserResponse(UUID.randomUUID(), "Test User", "user@test.com", 10);
        when(userRepository.findTopUsersByOrderCountOptimized(any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(topUser));

        List<TopUserResponse> result = reportService.getTopUsers(null, null);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);

        verify(userRepository).findTopUsersByOrderCountOptimized(isNull(), isNull(), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should return empty list when no top users found")
    void shouldReturnEmptyListWhenNoTopUsersFound() {
        when(userRepository.findTopUsersByOrderCountOptimized(any(), any(), any(PageRequest.class)))
                .thenReturn(List.of());

        List<TopUserResponse> result = reportService.getTopUsers(startDate, endDate);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(userRepository).findTopUsersByOrderCountOptimized(eq(startDate), eq(endDate), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should get average ticket by user with date filter")
    void shouldGetAverageTicketByUserWithDateFilter() {
        UserAverageTicketResponse avgTicket = new UserAverageTicketResponse(UUID.randomUUID(), "Test User", "user@test.com", new BigDecimal("150.00"));
        when(orderRepository.findAverageTicketByAllUsers(any(), any()))
                .thenReturn(List.of(avgTicket));

        List<UserAverageTicketResponse> result = reportService.getAverageTicketByUser(startDate, endDate);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("user@test.com");
        assertThat(result.get(0).getAverageTicket()).isEqualByComparingTo(new BigDecimal("150.00"));

        verify(orderRepository).findAverageTicketByAllUsers(startDate, endDate);
    }

    @Test
    @DisplayName("Should get average ticket by user without date filter")
    void shouldGetAverageTicketByUserWithoutDateFilter() {
        UserAverageTicketResponse avgTicket = new UserAverageTicketResponse(UUID.randomUUID(), "Test User", "user@test.com", new BigDecimal("150.00"));
        when(orderRepository.findAverageTicketByAllUsers(any(), any()))
                .thenReturn(List.of(avgTicket));

        List<UserAverageTicketResponse> result = reportService.getAverageTicketByUser(null, null);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);

        verify(orderRepository).findAverageTicketByAllUsers(isNull(), isNull());
    }

    @Test
    @DisplayName("Should return empty list when no average ticket data found")
    void shouldReturnEmptyListWhenNoAverageTicketDataFound() {
        when(orderRepository.findAverageTicketByAllUsers(any(), any()))
                .thenReturn(List.of());

        List<UserAverageTicketResponse> result = reportService.getAverageTicketByUser(startDate, endDate);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(orderRepository).findAverageTicketByAllUsers(startDate, endDate);
    }

    @Test
    @DisplayName("Should get current month revenue")
    void shouldGetCurrentMonthRevenue() {
        when(orderRepository.findTotalRevenueCurrentMonth()).thenReturn(new BigDecimal("5000.00"));
        when(orderRepository.countOrdersCurrentMonth()).thenReturn(10L);

        MonthlyRevenueResponse result = reportService.getCurrentMonthRevenue();

        assertThat(result).isNotNull();
        assertThat(result.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.getTotalOrders()).isEqualTo(10L);
        assertThat(result.getAverageOrderValue()).isEqualByComparingTo(new BigDecimal("500.00"));

        verify(orderRepository).findTotalRevenueCurrentMonth();
        verify(orderRepository).countOrdersCurrentMonth();
    }

    @Test
    @DisplayName("Should return zero revenue when no orders in current month")
    void shouldReturnZeroRevenueWhenNoOrdersInCurrentMonth() {
        when(orderRepository.findTotalRevenueCurrentMonth()).thenReturn(null);
        when(orderRepository.countOrdersCurrentMonth()).thenReturn(0L);

        MonthlyRevenueResponse result = reportService.getCurrentMonthRevenue();

        assertThat(result).isNotNull();
        assertThat(result.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalOrders()).isEqualTo(0L);
        assertThat(result.getAverageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(orderRepository).findTotalRevenueCurrentMonth();
        verify(orderRepository).countOrdersCurrentMonth();
    }

    @Test
    @DisplayName("Should handle null revenue gracefully")
    void shouldHandleNullRevenueGracefully() {
        when(orderRepository.findTotalRevenueCurrentMonth()).thenReturn(null);
        when(orderRepository.countOrdersCurrentMonth()).thenReturn(5L);

        MonthlyRevenueResponse result = reportService.getCurrentMonthRevenue();

        assertThat(result).isNotNull();
        assertThat(result.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAverageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(orderRepository).findTotalRevenueCurrentMonth();
        verify(orderRepository).countOrdersCurrentMonth();
    }
}
