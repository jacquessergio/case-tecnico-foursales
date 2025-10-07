package com.foursales.ecommerce.controller;

import com.foursales.ecommerce.config.SwaggerResponses;
import com.foursales.ecommerce.dto.MonthlyRevenueResponse;
import com.foursales.ecommerce.dto.TopUserResponse;
import com.foursales.ecommerce.dto.UserAverageTicketResponse;
import com.foursales.ecommerce.service.IReportService;
import com.foursales.ecommerce.util.DateParseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Endpoints for report generation - admin only (API v1)")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ReportController {

    private final IReportService reportService;

    @Operation(summary = "Top 5 buyer users", description = "Returns the top 5 users who made the most purchases. Supports date filtering with flexible formats: ISO DateTime (2025-10-01T00:00:00) or simple DATE (2025-10-01)")
    @ApiResponse(responseCode = "200", description = "List of top users returned successfully", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TopUserResponse.class))))
    @SwaggerResponses.Forbidden
    @SwaggerResponses.InternalError
    @GetMapping("/top-users")
    public List<TopUserResponse> getTopUsers(
            @Parameter(description = "Start date - accepts ISO DateTime (2025-10-01T00:00:00) or DATE (2025-10-01)", example = "2025-10-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "End date - accepts ISO DateTime (2025-10-01T23:59:59) or DATE (2025-10-31)", example = "2025-10-31")
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = DateParseUtils.parseStartDate(startDate);
        LocalDateTime end = DateParseUtils.parseEndDate(endDate);

        return reportService.getTopUsers(start, end);
    }

    @Operation(summary = "Average ticket per user", description = "Returns the average order value per user. Supports date filtering with flexible formats: ISO DateTime (2025-10-01T00:00:00) or simple DATE (2025-10-01)")
    @ApiResponse(responseCode = "200", description = "List of average tickets returned successfully", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserAverageTicketResponse.class))))
    @SwaggerResponses.Forbidden
    @SwaggerResponses.InternalError
    @GetMapping("/average-ticket")
    public List<UserAverageTicketResponse> getAverageTicketByUser(
            @Parameter(description = "Start date - accepts ISO DateTime (2025-10-01T00:00:00) or DATE (2025-10-01)", example = "2025-10-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "End date - accepts ISO DateTime (2025-10-01T23:59:59) or DATE (2025-10-31)", example = "2025-10-31")
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = DateParseUtils.parseStartDate(startDate);
        LocalDateTime end = DateParseUtils.parseEndDate(endDate);

        return reportService.getAverageTicketByUser(start, end);
    }

    @Operation(summary = "Current month revenue")
    @ApiResponse(responseCode = "200", description = "Revenue data returned successfully", content = @Content(schema = @Schema(implementation = MonthlyRevenueResponse.class)))
    @SwaggerResponses.Forbidden
    @SwaggerResponses.InternalError
    @GetMapping("/current-month-revenue")
    public MonthlyRevenueResponse getCurrentMonthRevenue() {
        return reportService.getCurrentMonthRevenue();
    }
}