package com.foursales.ecommerce.dto;

import com.foursales.ecommerce.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for new user registration")
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Schema(description = "User full name", example = "João Silva", minLength = 3, maxLength = 50)
    private String name;

    @NotBlank
    @Email
    @Schema(description = "User email address", example = "joao.silva@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$", message = "Password must contain at least 8 characters, including uppercase, lowercase, number, and special character (@$!%*?&)")
    @Schema(description = "User password (minimum 8 characters: uppercase letters, lowercase letters, numbers and special characters @$!%*?&)", example = "MySecure@Pass123", minLength = 8, maxLength = 128)
    private String password;

    @NotNull
    @Schema(description = "User role in the system", example = "USER", allowableValues = { "USER", "ADMIN" })
    private UserRole role;
}