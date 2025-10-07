package com.foursales.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.ecommerce.dto.CreateOrderRequest;
import com.foursales.ecommerce.dto.OrderItemRequest;
import com.foursales.ecommerce.dto.OrderResponse;
import com.foursales.ecommerce.dto.PaymentResponse;
import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.config.TestConfig;
import com.foursales.ecommerce.enums.OrderStatus;
import com.foursales.ecommerce.enums.UserRole;
import com.foursales.ecommerce.exception.BusinessException;
import com.foursales.ecommerce.exception.ResourceNotFoundException;
import com.foursales.ecommerce.repository.jpa.UserRepository;
import com.foursales.ecommerce.security.JwtTokenProvider;
import com.foursales.ecommerce.service.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.elasticsearch.uris=",
    "spring.data.elasticsearch.repositories.enabled=false",
    "management.health.elasticsearch.enabled=false"
})
@Import(TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IOrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private UUID orderId;
    private UUID productId;
    private String userToken;
    private CreateOrderRequest createOrderRequest;
    private OrderResponse orderResponse;
    private PaymentResponse paymentResponse;

    @BeforeEach
    void setUp() {
        testUser = new User("Test User", "test@test.com", passwordEncoder.encode("password"), UserRole.USER);
        testUser = userRepository.save(testUser);

        org.springframework.security.core.Authentication auth =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                testUser, null, testUser.getAuthorities());
        userToken = jwtTokenProvider.generateToken(auth);

        orderId = UUID.randomUUID();
        productId = UUID.randomUUID();

        OrderItemRequest itemRequest = new OrderItemRequest(productId, 2);
        createOrderRequest = new CreateOrderRequest(List.of(itemRequest), null);

        orderResponse = OrderResponse.builder()
                .id(orderId)
                .userId(testUser.getId())
                .userName("Test User")
                .userEmail("test@test.com")
                .totalValue(new BigDecimal("200.00"))
                .status(OrderStatus.PENDENTE)
                .items(List.of())
                .build();

        paymentResponse = new PaymentResponse(
                orderId,
                OrderStatus.PAGO,
                new BigDecimal("200.00"),
                LocalDateTime.now(),
                "Payment processed successfully"
        );
    }

    @Test
    @DisplayName("Should get user orders")
    void shouldGetUserOrders() throws Exception {
        when(orderService.getOrdersByUser(any(User.class))).thenReturn(List.of(orderResponse));

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.[0].status").value("PENDENTE"));

        verify(orderService).getOrdersByUser(any(User.class));
    }

    @Test
    @DisplayName("Should get order by id")
    void shouldGetOrderById() throws Exception {
        when(orderService.getOrderByIdForUser(any(UUID.class), any(User.class)))
                .thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));

        verify(orderService).getOrderByIdForUser(any(UUID.class), any(User.class));
    }

    @Test
    @DisplayName("Should return 404 when order not found")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(orderService.getOrderByIdForUser(any(UUID.class), any(User.class)))
                .thenThrow(new ResourceNotFoundException("Order", "id", orderId));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        verify(orderService).getOrderByIdForUser(any(UUID.class), any(User.class));
    }

    @Test
    @DisplayName("Should return 400 when accessing order from another user")
    void shouldReturn400WhenAccessingOrderFromAnotherUser() throws Exception {
        when(orderService.getOrderByIdForUser(any(UUID.class), any(User.class)))
                .thenThrow(new BusinessException("Order does not belong to user"));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());

        verify(orderService).getOrderByIdForUser(any(UUID.class), any(User.class));
    }

    @Test
    @DisplayName("Should create order successfully")
    void shouldCreateOrderSuccessfully() throws Exception {
        when(orderService.createOrder(any(User.class), any(CreateOrderRequest.class)))
                .thenReturn(orderResponse);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        verify(orderService).createOrder(any(User.class), any(CreateOrderRequest.class));
    }

    @Test
    @DisplayName("Should return 400 when creating order with invalid data")
    void shouldReturn400WhenCreatingOrderWithInvalidData() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(List.of(), null);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    @DisplayName("Should return 404 when creating order with non-existent product")
    void shouldReturn404WhenCreatingOrderWithNonExistentProduct() throws Exception {
        when(orderService.createOrder(any(User.class), any(CreateOrderRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product", "id", productId));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isNotFound());

        verify(orderService).createOrder(any(User.class), any(CreateOrderRequest.class));
    }

    @Test
    @DisplayName("Should pay order successfully")
    void shouldPayOrderSuccessfully() throws Exception {
        when(orderService.payOrder(any(UUID.class), any(User.class)))
                .thenReturn(paymentResponse);

        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.message").value("Payment processed successfully"));

        verify(orderService).payOrder(any(UUID.class), any(User.class));
    }

    @Test
    @DisplayName("Should return 400 when trying to pay order with invalid status")
    void shouldReturn400WhenTryingToPayOrderWithInvalidStatus() throws Exception {
        when(orderService.payOrder(any(UUID.class), any(User.class)))
                .thenThrow(new BusinessException("Order cannot be paid. Current status: PAGO"));

        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());

        verify(orderService).payOrder(any(UUID.class), any(User.class));
    }

    @Test
    @DisplayName("Should return 404 when trying to pay non-existent order")
    void shouldReturn404WhenTryingToPayNonExistentOrder() throws Exception {
        when(orderService.payOrder(any(UUID.class), any(User.class)))
                .thenThrow(new ResourceNotFoundException("Order", "id", orderId));

        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        verify(orderService).payOrder(any(UUID.class), any(User.class));
    }

    @Test
    @DisplayName("Should return 401 when accessing orders without authentication")
    void shouldReturn401WhenAccessingOrdersWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).getOrdersByUser(any());
    }
}
