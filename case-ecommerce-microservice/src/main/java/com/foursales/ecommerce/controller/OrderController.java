package com.foursales.ecommerce.controller;

import com.foursales.ecommerce.config.SwaggerResponses;
import com.foursales.ecommerce.dto.CreateOrderRequest;
import com.foursales.ecommerce.dto.OrderResponse;
import com.foursales.ecommerce.dto.PaymentResponse;
import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order management endpoints (API v1)")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    @Operation(summary = "List user orders")
    @ApiResponse(responseCode = "200", description = "List of orders returned successfully", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderResponse.class))))
    @SwaggerResponses.InternalError
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getUserOrders(@AuthenticationPrincipal User user) {
        List<OrderResponse> orders = orderService.getOrdersByUser(user);
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Get order by ID")
    @ApiResponse(responseCode = "200", description = "Order found", content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @SwaggerResponses.NotFound
    @SwaggerResponses.InternalError
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order ID") @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        OrderResponse order = orderService.getOrderByIdForUser(id, user);
        return ResponseEntity.ok(order);
    }

    @Operation(summary = "Create new order")
    @ApiResponse(responseCode = "201", description = "Order created successfully", content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @SwaggerResponses.BadRequest
    @SwaggerResponses.NotFound
    @SwaggerResponses.InternalError
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User user) {

        OrderResponse order = orderService.createOrder(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @Operation(summary = "Process order payment")
    @ApiResponse(responseCode = "200", description = "Payment processed successfully", content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @SwaggerResponses.BadRequest
    @SwaggerResponses.Forbidden
    @SwaggerResponses.NotFound
    @SwaggerResponses.InternalError
    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentResponse> payOrder(
            @Parameter(description = "Order ID") @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        PaymentResponse response = orderService.payOrder(id, user);
        return ResponseEntity.ok(response);
    }
}