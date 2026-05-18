package de.raphaellee.transflow.fulfillment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fulfillments")
@Tag(name = "Fulfillments", description = "Inspect fulfillment records")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping
    @Operation(summary = "List all fulfillment records")
    @ApiResponse(responseCode = "200", description = "Fulfillment records returned")
    public ResponseEntity<List<FulfillmentResponse>> list() {
        return ResponseEntity.ok(fulfillmentService.listAll());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get fulfillment record by order ID")
    @ApiResponse(responseCode = "200", description = "Fulfillment record found")
    @ApiResponse(responseCode = "404", description = "No fulfillment record for this order")
    public ResponseEntity<FulfillmentResponse> getByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(fulfillmentService.getByOrderId(orderId));
    }
}
