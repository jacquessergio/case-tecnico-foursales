package com.foursales.ecommerce.repository.jpa;

import com.foursales.ecommerce.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByCategory(String category);

    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0")
    List<Product> findAllInStock();

    List<Product> findByNameContainingIgnoreCase(String name);

    /**
     * PESSIMISTIC LOCKING: Prevents race condition in stock reduction
     * Acquires database-level write lock on product row, blocking concurrent transactions
     * until current transaction commits or rolls back.
     *
     * Used by OrderService.createOrder() to atomically check and reserve stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);
}