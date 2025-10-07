package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response containing JWT token")
public class JwtResponse {

    @Schema(description = "JWT token for authentication", example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2FvLnNpbHZhQGV4YW1wbGUuY29tIiwiaWF0IjoxNjE2MjM5MDIyfQ.xyz")
    private String token;

    @Schema(description = "Token type", example = "Bearer", defaultValue = "Bearer")
    private String type = "Bearer";

    @Schema(description = "Authenticated user email", example = "joao.silva@example.com")
    private String email;

    @Schema(description = "User role in the system", example = "USER", allowableValues = { "USER", "ADMIN" })
    private String role;

    public JwtResponse(String token, String email, String role) {
        this.token = token;
        this.email = email;
        this.role = role;
    }
}