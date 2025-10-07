package com.foursales.ecommerce.service;

import com.foursales.ecommerce.constant.AppConstants;
import com.foursales.ecommerce.dto.CreateOrderRequest;
import com.foursales.ecommerce.dto.OrderItemRequest;
import com.foursales.ecommerce.dto.OrderResponse;
import com.foursales.ecommerce.dto.PaymentResponse;
import com.foursales.ecommerce.entity.Order;
import com.foursales.ecommerce.entity.OrderItem;
import com.foursales.ecommerce.entity.Product;
import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.enums.OrderStatus;
import com.foursales.ecommerce.exception.BusinessException;
import com.foursales.ecommerce.exception.ResourceNotFoundException;
import com.foursales.ecommerce.mapper.OrderMapper;
import com.foursales.ecommerce.outbox.OutboxService;
import com.foursales.ecommerce.repository.jpa.OrderRepository;
import com.foursales.ecommerce.repository.jpa.ProductRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final OutboxService outboxService;

    @Override
    @CircuitBreaker(name = "mysql")
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return orderMapper.toResponse(order);
    }

    @Override
    @CircuitBreaker(name = "mysql")
    public OrderResponse getOrderByIdForUser(UUID id, User user) {
        Order order = orderRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (!order.getUser().equals(user)) {
            throw new BusinessException("Order does not belong to user");
        }

        return orderMapper.toResponse(order);
    }

    @Override
    @CircuitBreaker(name = "mysql")
    public List<OrderResponse> getOrdersByUser(User user) {
        List<Order> orders = orderRepository.findByUser(user);
        return orderMapper.toResponseList(orders);
    }

    @Override
    public OrderResponse createOrder(User user, CreateOrderRequest request) {
        // IDEMPOTENCY: Returns existing order if key matches to prevent duplicate orders
        if (request.hasIdempotencyKey()) {
            Optional<Order> existingOrder = orderRepository.findByUserAndIdempotencyKey(
                    user, request.getIdempotencyKey());

            if (existingOrder.isPresent()) {
                log.info("Idempotency: Returning existing order {} for key {}",
                        existingOrder.get().getId(), request.getIdempotencyKey());
                return orderMapper.toResponse(existingOrder.get());
            }
        }

        Order order = request.hasIdempotencyKey()
                ? new Order(user, request.getIdempotencyKey())
                : new Order(user);

        boolean hasInsufficientStock = false;

        for (OrderItemRequest itemRequest : request.getItems()) {
            UUID productId = itemRequest.getProductId();
            Integer quantity = itemRequest.getQuantity();

            // PESSIMISTIC LOCKING: Prevents TOCTOU race condition in stock check
            Optional<Product> productOpt = productRepository.findByIdForUpdate(productId);
            if (productOpt.isEmpty()) {
                throw new ResourceNotFoundException("Product", "id", productId);
            }

            Product product = productOpt.get();

            if (!product.hasStock(quantity)) {
                hasInsufficientStock = true;
                break;
            }

            OrderItem orderItem = new OrderItem(order, product, quantity, product.getPrice());
            order.addItem(orderItem);
        }

        if (hasInsufficientStock) {
            order.markAsCancelled();
        }

        Order savedOrder = orderRepository.save(order);

        if (request.getIdempotencyKey() != null) {
            log.info("Order created with idempotency key: {}", request.getIdempotencyKey());
        }

        return orderMapper.toResponse(savedOrder);
    }

    @Override
    public PaymentResponse payOrder(UUID orderId, User user) {
        Order order = orderRepository.findByIdWithUser(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUser().equals(user)) {
            throw new BusinessException("Order does not belong to user");
        }

        if (order.getStatus() != OrderStatus.PENDENTE) {
            throw new BusinessException("Order cannot be paid. Current status: " + order.getStatus());
        }

        order.markAsPaid();
        Order paidOrder = orderRepository.save(order);

        // OUTBOX PATTERN: Ensures event delivery even if Kafka is down
        outboxService.saveEvent(
                "ORDER",
                paidOrder.getId().toString(),
                "ORDER_PAID",
                paidOrder,
                AppConstants.TOPIC_ORDER_PAID);

        return new PaymentResponse(
                paidOrder.getId(),
                paidOrder.getStatus(),
                paidOrder.getTotalValue(),
                paidOrder.getPaymentDate(),
                "Payment processed successfully");
    }
}
