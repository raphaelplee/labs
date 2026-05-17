package de.raphaellee.transflow.fulfillment;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "transflow", name = "fulfillment_records")
class FulfillmentRecord {
    @Id
    private UUID id;

    @Column(name = "order_id", updatable = false, nullable = false)
    private UUID orderId;

    @Column(updatable = false, nullable = false)
    private String subscriptionId;

    private String status;

    @Column(updatable = false)
    private Instant fulfilledAt;

    protected FulfillmentRecord() {}

    FulfillmentRecord(UUID orderId, String subscriptionId) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.orderId = orderId;
        this.subscriptionId = subscriptionId;
        this.status = "FULFILLED";
        this.fulfilledAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getOrderId() { return orderId; }
    String getSubscriptionId() { return subscriptionId; }
    String getStatus() { return status; }
    Instant getFulfilledAt() { return fulfilledAt; }
}
