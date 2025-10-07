package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed monthly revenue data")
public class MonthlyRevenueResponse {

    @Schema(description = "Reference month", example = "JANUARY")
    private String month;

    @Schema(description = "Reference year", example = "2025")
    private Integer year;

    @Schema(description = "Total revenue for the month", example = "15750.00")
    private BigDecimal totalRevenue;

    @Schema(description = "Total number of paid orders in the month", example = "42")
    private Long totalOrders;

    @Schema(description = "Average order value for the month", example = "375.00")
    private BigDecimal averageOrderValue;

    @Schema(description = "Period start date (first day of the month)", example = "2025-01-01T00:00:00")
    private LocalDateTime periodStart;

    @Schema(description = "Period end date (current date)", example = "2025-01-15T14:30:00")
    private LocalDateTime periodEnd;
}
