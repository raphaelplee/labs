package de.raphaellee.transflow.payment;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "transflow", name = "payments")
class Payment {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "order_id", updatable = false, nullable = false)
    private UUID orderId;

    private String status;
    private String scenario;

    @Column(updatable = false)
    private Instant createdAt;

    protected Payment() {}

    Payment(UUID orderId, String status, String scenario) {
        this.orderId = orderId;
        this.status = status;
        this.scenario = scenario;
        this.createdAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getOrderId() { return orderId; }
    String getStatus() { return status; }
    String getScenario() { return scenario; }
    Instant getCreatedAt() { return createdAt; }
}
