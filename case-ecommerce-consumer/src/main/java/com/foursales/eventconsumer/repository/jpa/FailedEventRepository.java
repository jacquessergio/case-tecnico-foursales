package com.foursales.eventconsumer.repository.jpa;

import com.foursales.eventconsumer.entity.FailedEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, UUID> {

       @Query("SELECT fe FROM FailedEvent fe " +
                     "WHERE fe.status IN ('PENDING', 'RETRYING') " +
                     "AND fe.nextRetryAt <= :now " +
                     "ORDER BY fe.nextRetryAt ASC")
       List<FailedEvent> findEventsReadyForRetry(@Param("now") LocalDateTime now, Pageable pageable);

       @Modifying
       @Query("DELETE FROM FailedEvent fe " +
                     "WHERE fe.status = 'PROCESSED' " +
                     "AND fe.processedAt < :cutoffDate")
       int deleteOldProcessedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);

       @Query("SELECT fe FROM FailedEvent fe " +
                     "WHERE fe.status = 'RETRYING' " +
                     "AND fe.lastRetryAt < :stuckThreshold")
       List<FailedEvent> findStuckRetryingEvents(@Param("stuckThreshold") LocalDateTime stuckThreshold);

       @Query("SELECT " +
                     "COUNT(CASE WHEN fe.status = 'PENDING' THEN 1 END) as pendingCount, " +
                     "COUNT(CASE WHEN fe.status = 'RETRYING' THEN 1 END) as retryingCount, " +
                     "COUNT(CASE WHEN fe.status = 'PROCESSED' THEN 1 END) as processedCount, " +
                     "COUNT(CASE WHEN fe.status = 'FAILED' THEN 1 END) as failedCount, " +
                     "COUNT(CASE WHEN fe.status = 'MAX_RETRIES_REACHED' THEN 1 END) as maxRetriesCount " +
                     "FROM FailedEvent fe")
       FailedEventStats getStatistics();

       interface FailedEventStats {
              Long getPendingCount();

              Long getRetryingCount();

              Long getProcessedCount();

              Long getFailedCount();

              Long getMaxRetriesCount();
       }
}