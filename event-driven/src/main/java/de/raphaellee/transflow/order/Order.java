package de.raphaellee.transflow.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "transflow", name = "orders")
class Order {
    @Id
    private UUID id;

    @Column(unique = true)
    private String subscriptionId;

    private String status;

    @Column(updatable = false)
    private Instant createdAt;

    protected Order() {}

    Order(UUID id, String subscriptionId) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.status = "CREATED";
        this.createdAt = Instant.now();
    }

    UUID getId() { return id; }
    String getSubscriptionId() { return subscriptionId; }
    String getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
}
