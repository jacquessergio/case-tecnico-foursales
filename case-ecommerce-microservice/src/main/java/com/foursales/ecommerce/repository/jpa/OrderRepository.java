package com.foursales.ecommerce.repository.jpa;

import com.foursales.ecommerce.entity.Order;
import com.foursales.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.product " +
            "LEFT JOIN FETCH o.user " +
            "WHERE o.user = :user " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByUser(@Param("user") User user);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.product " +
            "LEFT JOIN FETCH o.user " +
            "WHERE o.id = :id")
    Optional<Order> findByIdWithUser(@Param("id") UUID id);

    @Query("SELECT SUM(o.totalValue) FROM Order o " +
            "WHERE o.status = 'PAGO' " +
            "AND YEAR(o.paymentDate) = YEAR(CURRENT_DATE) " +
            "AND MONTH(o.paymentDate) = MONTH(CURRENT_DATE)")
    BigDecimal findTotalRevenueCurrentMonth();

    @Query("SELECT COUNT(o) FROM Order o " +
            "WHERE o.status = 'PAGO' " +
            "AND YEAR(o.paymentDate) = YEAR(CURRENT_DATE) " +
            "AND MONTH(o.paymentDate) = MONTH(CURRENT_DATE)")
    Long countOrdersCurrentMonth();

    @Query("""
            SELECT new com.foursales.ecommerce.dto.UserAverageTicketResponse(
                       u.id, u.name, u.email, COALESCE(AVG(o.totalValue), 0)
                   )
                   FROM User u
                   LEFT JOIN u.orders o ON (
                       (:startDate IS NULL OR o.createdAt >= :startDate) AND
                       (:endDate IS NULL OR o.createdAt <= :endDate)
                   )
                   GROUP BY u.id, u.name, u.email
                   """)
    List<com.foursales.ecommerce.dto.UserAverageTicketResponse> findAverageTicketByAllUsers(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * IDEMPOTENCY: Find existing order by user and idempotency key
     * Used to prevent duplicate order creation from double-clicks or network
     * retries
     */
    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.product " +
            "LEFT JOIN FETCH o.user " +
            "WHERE o.user = :user AND o.idempotencyKey = :idempotencyKey")
    Optional<Order> findByUserAndIdempotencyKey(@Param("user") User user,
            @Param("idempotencyKey") String idempotencyKey);
}