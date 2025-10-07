package com.foursales.ecommerce.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit Breaker configuration to protect external integrations.
 *
 * Circuit Breaker Pattern: Prevents cascading failures by detecting failures
 * and "opening the circuit", failing fast without executing the operation.
 *
 * States:
 * - CLOSED: Normal, requests pass through
 * - OPEN: Detected failures, blocks requests (fail fast)
 * - HALF_OPEN: Tests if the service has recovered
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Configures event logging for circuit breakers after initialization.
     * Circuit breaker configurations come from application.yml via Spring Boot
     * auto-configuration
     */
    @PostConstruct
    public void configureCircuitBreakerEventLogging() {

        // Event logging for all circuit breakers
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(this::onCircuitBreakerAdded)
                .onEntryRemoved(this::onCircuitBreakerRemoved)
                .onEntryReplaced(this::onCircuitBreakerReplaced);

        log.info("Circuit Breaker event logging configured successfully");
    }

    /**
     * Log when circuit breaker is created.
     */
    private void onCircuitBreakerAdded(EntryAddedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> event) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = event.getAddedEntry();
        log.info("Circuit Breaker '{}' created with configuration: failureRate={}%, slowCallRate={}%",
                cb.getName(),
                cb.getCircuitBreakerConfig().getFailureRateThreshold(),
                cb.getCircuitBreakerConfig().getSlowCallRateThreshold());

        // State transition listener
        cb.getEventPublisher()
                .onStateTransition(stateEvent -> {
                    log.warn("Circuit Breaker '{}' changed state: {} → {}",
                            cb.getName(),
                            stateEvent.getStateTransition().getFromState(),
                            stateEvent.getStateTransition().getToState());
                })
                .onFailureRateExceeded(rateEvent -> {
                    log.error("Circuit Breaker '{}' exceeded failure rate: {}%",
                            cb.getName(),
                            rateEvent.getFailureRate());
                })
                .onSlowCallRateExceeded(rateEvent -> {
                    log.warn("Circuit Breaker '{}' exceeded slow call rate: {}%",
                            cb.getName(),
                            rateEvent.getSlowCallRate());
                });
    }

    private void onCircuitBreakerRemoved(
            EntryRemovedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> event) {
        log.info("Circuit Breaker '{}' removed", event.getRemovedEntry().getName());
    }

    private void onCircuitBreakerReplaced(
            EntryReplacedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> event) {
        log.info("Circuit Breaker '{}' replaced", event.getNewEntry().getName());
    }
}
