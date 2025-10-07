package com.foursales.ecommerce.controller.admin;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/circuit-breakers")
@RequiredArgsConstructor
@Tag(name = "Admin - Circuit Breaker", description = "Circuit breaker monitoring and control")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Hidden
public class CircuitBreakerAdminController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Operation(summary = "List status of all circuit breakers")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllCircuitBreakersStatus() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> circuitBreakers = new HashMap<>();

        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            Map<String, Object> cbData = new HashMap<>();
            cbData.put("state", cb.getState().toString());
            cbData.put("failureRate", String.format("%.2f%%", cb.getMetrics().getFailureRate()));
            cbData.put("slowCallRate", String.format("%.2f%%", cb.getMetrics().getSlowCallRate()));
            cbData.put("numberOfSuccessfulCalls", cb.getMetrics().getNumberOfSuccessfulCalls());
            cbData.put("numberOfFailedCalls", cb.getMetrics().getNumberOfFailedCalls());
            cbData.put("numberOfSlowCalls", cb.getMetrics().getNumberOfSlowCalls());
            cbData.put("numberOfNotPermittedCalls", cb.getMetrics().getNumberOfNotPermittedCalls());

            var config = cb.getCircuitBreakerConfig();
            Map<String, Object> configData = new HashMap<>();
            configData.put("failureRateThreshold", config.getFailureRateThreshold() + "%");
            configData.put("slowCallDurationThreshold", config.getSlowCallDurationThreshold().toSeconds() + "s");
            configData.put("slowCallRateThreshold", config.getSlowCallRateThreshold() + "%");
            configData.put("waitDurationInOpenState",
                    config.getWaitIntervalFunctionInOpenState().apply(1) / 1000 + "s");
            configData.put("slidingWindowSize", config.getSlidingWindowSize());
            configData.put("minimumNumberOfCalls", config.getMinimumNumberOfCalls());

            cbData.put("config", configData);
            circuitBreakers.put(cb.getName(), cbData);
        });

        response.put("circuitBreakers", circuitBreakers);
        response.put("count", circuitBreakers.size());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get status of a specific circuit breaker")
    @GetMapping("/{name}/status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus(@PathVariable String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);

        Map<String, Object> response = new HashMap<>();
        response.put("name", name);
        response.put("state", cb.getState().toString());
        response.put("metrics", Map.of(
                "failureRate", cb.getMetrics().getFailureRate(),
                "slowCallRate", cb.getMetrics().getSlowCallRate(),
                "numberOfSuccessfulCalls", cb.getMetrics().getNumberOfSuccessfulCalls(),
                "numberOfFailedCalls", cb.getMetrics().getNumberOfFailedCalls(),
                "numberOfSlowCalls", cb.getMetrics().getNumberOfSlowCalls()));

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Force circuit breaker state transition")
    @PostMapping("/{name}/transition")
    public ResponseEntity<Map<String, String>> transitionCircuitBreakerState(
            @PathVariable String name,
            @RequestParam String targetState) {

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);

        try {
            switch (targetState.toUpperCase()) {
                case "CLOSED":
                    cb.transitionToClosedState();
                    break;
                case "OPEN":
                    cb.transitionToOpenState();
                    break;
                case "FORCED_OPEN":
                    cb.transitionToForcedOpenState();
                    break;
                case "DISABLED":
                    cb.transitionToDisabledState();
                    break;
                default:
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Invalid target state. Use: CLOSED, OPEN, FORCED_OPEN, or DISABLED"));
            }

            log.info("Circuit breaker '{}' transitioned to {}", name, targetState);

            return ResponseEntity.ok(Map.of(
                    "message", "Circuit breaker transitioned successfully",
                    "name", name,
                    "newState", cb.getState().toString()));

        } catch (Exception e) {
            log.error("Error transitioning circuit breaker '{}': {}", name, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to transition circuit breaker: " + e.getMessage()));
        }
    }
}
