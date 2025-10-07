package com.foursales.ecommerce.service;

import com.foursales.ecommerce.dto.CreateOrderRequest;
import com.foursales.ecommerce.dto.OrderResponse;
import com.foursales.ecommerce.dto.PaymentResponse;
import com.foursales.ecommerce.entity.User;

import java.util.List;
import java.util.UUID;

public interface IOrderService {
    
    OrderResponse getOrderById(UUID id);

    OrderResponse getOrderByIdForUser(UUID id, User user);

    List<OrderResponse> getOrdersByUser(User user);

    OrderResponse createOrder(User user, CreateOrderRequest request);

    PaymentResponse payOrder(UUID orderId, User user);
}
