package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Top buyer user data")
public class TopUserResponse {

    @Schema(description = "User ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "User name", example = "João Silva")
    private String name;

    @Schema(description = "User email", example = "joao.silva@email.com")
    private String email;

    @Schema(description = "Number of orders placed", example = "15")
    private Integer orderCount;
}
