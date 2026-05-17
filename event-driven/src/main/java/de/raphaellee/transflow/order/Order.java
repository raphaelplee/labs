package de.raphaellee.transflow.order;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "transflow", name = "orders")
class Order {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(unique = true)
    private UUID subscriptionId;

    private String status;

    @Column(updatable = false)
    private Instant createdAt;

    protected Order() {}

    Order(UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.status = "CREATED";
        this.createdAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getSubscriptionId() { return subscriptionId; }
    String getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
}
