package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User average ticket")
public class UserAverageTicketResponse {

    @Schema(description = "User ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "User name", example = "Maria Souza")
    private String name;

    @Schema(description = "User email", example = "maria.souza@email.com")
    private String email;

    @Schema(description = "Average value of user orders", example = "450.75")
    private BigDecimal averageTicket;

    /**
     * Constructor for native query projections that return Double instead of BigDecimal
     * Converts Double to BigDecimal automatically
     */
    public UserAverageTicketResponse(UUID id, String name, String email, Double averageTicket) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.averageTicket = averageTicket != null ? BigDecimal.valueOf(averageTicket) : BigDecimal.ZERO;
    }
}
