package com.foursales.ecommerce.service;

import com.foursales.ecommerce.dto.JwtResponse;
import com.foursales.ecommerce.dto.LoginRequest;
import com.foursales.ecommerce.dto.RegisterRequest;
import com.foursales.ecommerce.dto.RegisterResponse;

public interface IAuthService {

    JwtResponse login(LoginRequest loginRequest);

    RegisterResponse register(RegisterRequest registerRequest);
}
