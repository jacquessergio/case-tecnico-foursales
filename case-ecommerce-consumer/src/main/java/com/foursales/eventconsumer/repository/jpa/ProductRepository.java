package com.foursales.eventconsumer.repository.jpa;

import com.foursales.eventconsumer.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * 🔒 PESSIMISTIC LOCKING: Prevents race condition in stock reduction
     * Acquires database-level write lock on product row during stock update.
     *
     * Used by StockUpdateService to atomically reduce stock when processing
     * order payment events from Kafka.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);
}