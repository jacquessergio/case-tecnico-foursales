package com.foursales.ecommerce.repository.jpa;

import com.foursales.ecommerce.dto.TopUserResponse;
import com.foursales.ecommerce.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            SELECT new com.foursales.ecommerce.dto.TopUserResponse(
                u.id, u.name, u.email, CAST(COUNT(o) AS integer)
            )
            FROM User u JOIN u.orders o
            WHERE (:startDate IS NULL OR o.createdAt >= :startDate)
            AND (:endDate IS NULL OR o.createdAt <= :endDate)
            GROUP BY u.id, u.name, u.email
            ORDER BY COUNT(o) DESC
            """)
    List<TopUserResponse> findTopUsersByOrderCountOptimized(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
