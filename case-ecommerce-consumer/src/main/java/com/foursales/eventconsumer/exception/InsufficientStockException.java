package com.foursales.eventconsumer.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final UUID productId;
    private final Integer requested;
    private final Integer available;

    public InsufficientStockException(UUID productId, Integer requested, Integer available) {
        super(String.format("Insufficient stock for product %s: requested=%d, available=%d",
                productId, requested, available));
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }
}
