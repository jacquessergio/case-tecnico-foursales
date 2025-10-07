package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User registration response")
public class RegisterResponse {

    @Schema(description = "Success or error message", example = "User registered successfully!")
    private String message;
}
