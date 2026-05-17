package de.raphaellee.transflow.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "transflow", name = "orders")
class Order {
    @Id
    UUID id;
    String subscriptionId;
    String status;
    Instant createdAt;

    protected Order() {}

    Order(UUID id, String subscriptionId) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.status = "CREATED";
        this.createdAt = Instant.now();
    }
}
