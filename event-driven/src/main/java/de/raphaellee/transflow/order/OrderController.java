package de.raphaellee.transflow.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Create and inspect orders")
public class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create a new order and start a saga")
    @ApiResponse(responseCode = "201", description = "Order created, saga started")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "409", description = "Active order already exists for this subscription")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        var response = orderService.createOrder(request.subscriptionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
}
