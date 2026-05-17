package de.raphaellee.transflow.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface FulfillmentRecordRepository extends JpaRepository<FulfillmentRecord, UUID> {
    Optional<FulfillmentRecord> findByOrderId(UUID orderId);
}
