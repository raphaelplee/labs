package de.raphaellee.transflow.fulfillment;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "transflow", name = "fulfillment_records")
class FulfillmentRecord {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "order_id", updatable = false, nullable = false)
    private UUID orderId;

    @Column(updatable = false, nullable = false)
    private UUID subscriptionId;

    private String status;

    @Column(updatable = false)
    private Instant fulfilledAt;

    protected FulfillmentRecord() {}

    FulfillmentRecord(UUID orderId, UUID subscriptionId) {
        this.orderId = orderId;
        this.subscriptionId = subscriptionId;
        this.status = "FULFILLED";
        this.fulfilledAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getOrderId() { return orderId; }
    UUID getSubscriptionId() { return subscriptionId; }
    String getStatus() { return status; }
    Instant getFulfilledAt() { return fulfilledAt; }
}
