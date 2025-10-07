package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for user authentication")
public class LoginRequest {

    @NotBlank
    @Email
    @Schema(description = "User email address", example = "joao.silva@example.com")
    private String email;

    @NotBlank
    @Schema(description = "User password", example = "MySecure@Pass123")
    private String password;
}