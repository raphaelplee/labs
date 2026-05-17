package de.raphaellee.transflow.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findBySubscriptionId(UUID subscriptionId);
}
