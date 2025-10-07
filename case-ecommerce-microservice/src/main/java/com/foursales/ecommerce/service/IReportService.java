package com.foursales.ecommerce.service;

import com.foursales.ecommerce.dto.MonthlyRevenueResponse;
import com.foursales.ecommerce.dto.TopUserResponse;
import com.foursales.ecommerce.dto.UserAverageTicketResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface IReportService {

    List<TopUserResponse> getTopUsers(LocalDateTime startDate, LocalDateTime endDate);

    List<UserAverageTicketResponse> getAverageTicketByUser(LocalDateTime startDate, LocalDateTime endDate);

    MonthlyRevenueResponse getCurrentMonthRevenue();
}
