package com.foursales.eventconsumer.exception;

import java.util.UUID;

public class StockUpdateException extends RuntimeException {

    public StockUpdateException(String message) {
        super(message);
    }

    public StockUpdateException(String message, Throwable cause) {
        super(message, cause);
    }

    public StockUpdateException(UUID orderId, Throwable cause) {
        super("Failed to update stock for order: " + orderId, cause);
    }
}
