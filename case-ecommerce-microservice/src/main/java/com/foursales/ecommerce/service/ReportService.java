package com.foursales.ecommerce.service;

import com.foursales.ecommerce.constant.AppConstants;
import com.foursales.ecommerce.dto.MonthlyRevenueResponse;
import com.foursales.ecommerce.dto.TopUserResponse;
import com.foursales.ecommerce.dto.UserAverageTicketResponse;
import com.foursales.ecommerce.repository.jpa.OrderRepository;
import com.foursales.ecommerce.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService implements IReportService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Override
    public List<TopUserResponse> getTopUsers(LocalDateTime startDate, LocalDateTime endDate) {
        return userRepository.findTopUsersByOrderCountOptimized(
                startDate,
                endDate,
                PageRequest.of(0, AppConstants.TOP_USERS_LIMIT));
    }

    @Override
    public List<UserAverageTicketResponse> getAverageTicketByUser(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findAverageTicketByAllUsers(startDate, endDate);
    }

    @Override
    public MonthlyRevenueResponse getCurrentMonthRevenue() {
        BigDecimal revenue = orderRepository.findTotalRevenueCurrentMonth();
        LocalDateTime now = LocalDateTime.now();
        Long orderCount = orderRepository.countOrdersCurrentMonth();
        BigDecimal averageOrderValue = calculateAverage(revenue, orderCount);

        return new MonthlyRevenueResponse(
                now.getMonth().toString(),
                now.getYear(),
                revenue != null ? revenue : BigDecimal.ZERO,
                orderCount,
                averageOrderValue,
                now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0),
                now);
    }

    private BigDecimal calculateAverage(BigDecimal total, Long count) {
        if (count == null || count == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal safeTotal = total != null ? total : BigDecimal.ZERO;
        return safeTotal.divide(
                BigDecimal.valueOf(count),
                AppConstants.DECIMAL_SCALE,
                AppConstants.DEFAULT_ROUNDING_MODE);
    }
}