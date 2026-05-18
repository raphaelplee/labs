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
    @Operation(summary = "Confirm payment — triggers PAYMENT_OK saga signal")
    @ApiResponse(responseCode = "202", description = "Payment accepted, saga signalled")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "503", description = "Temporal workflow service unavailable")
    public ResponseEntity<PaymentResponse> confirm(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "HAPPY_PATH") PaymentScenario scenario) {
        var response = paymentService.confirmPayment(orderId, scenario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{orderId}/fail")
    @Operation(summary = "Fail payment — triggers PAYMENT_FAILED saga signal")
    @ApiResponse(responseCode = "202", description = "Payment failed, saga signalled")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "503", description = "Temporal workflow service unavailable")
    public ResponseEntity<PaymentResponse> fail(@PathVariable UUID orderId) {
        var response = paymentService.failPayment(orderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
