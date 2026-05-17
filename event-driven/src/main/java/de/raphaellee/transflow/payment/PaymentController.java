package de.raphaellee.transflow.payment;

import io.swagger.v3.oas.annotations.Operation;
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
    public ResponseEntity<PaymentResponse> confirm(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "happy-path") String scenario) {
        var response = paymentService.confirmPayment(orderId, scenario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{orderId}/fail")
    @Operation(summary = "Fail payment — triggers PAYMENT_FAILED saga signal")
    public ResponseEntity<PaymentResponse> fail(@PathVariable UUID orderId) {
        var response = paymentService.failPayment(orderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
