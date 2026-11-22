package de.raphaellee.transflow.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Trigger payment outcomes")
public class PaymentController {

    private final PaymentService paymentService;

    PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "Confirm payment — the saga receives paymentOk asynchronously")
    @ApiResponse(responseCode = "202", description = "Payment recorded, or the same outcome already recorded")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "409", description = "Payment already failed for this order")
    public ResponseEntity<PaymentResponse> confirm(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "HAPPY_PATH") PaymentScenario scenario) {
        var response = paymentService.confirmPayment(orderId, scenario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{orderId}/fail")
    @Operation(summary = "Fail payment — the saga receives paymentFailed asynchronously")
    @ApiResponse(responseCode = "202", description = "Payment recorded, or the same outcome already recorded")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "409", description = "Payment already processed for this order")
    public ResponseEntity<PaymentResponse> fail(@PathVariable UUID orderId) {
        var response = paymentService.failPayment(orderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
