package com.foursales.ecommerce.dto;

import com.foursales.ecommerce.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing the details of the successfully processed order payment")
public class PaymentResponse {

    @Schema(description = "Unique ID of the order that was paid", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID orderId;

    @Schema(description = "Updated order status after payment processing. After successful payment, the status changes from PENDENTE to PAGO, and an event is sent to Kafka for stock reduction.", example = "PAGO", allowableValues = {
            "PENDENTE", "PAGO", "CANCELADO" })
    private OrderStatus status;

    @Schema(description = "Total amount paid for the order, including all items", example = "7249.98")
    private BigDecimal valorTotal;

    @Schema(description = "Exact date and time when the payment was confirmed and recorded in the system", example = "2025-01-15T14:37:22")
    private LocalDateTime dataPagamento;

    @Schema(description = "Confirmation message informing the user that the payment was processed successfully", example = "Payment processed successfully")
    private String message;
}