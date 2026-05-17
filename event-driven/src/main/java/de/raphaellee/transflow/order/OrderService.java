package de.raphaellee.transflow.order;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final ApplicationEventPublisher publisher;

    OrderService(OrderRepository repository, ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional
    public OrderResponse createOrder(String subscriptionId) {
        repository.findBySubscriptionId(subscriptionId).ifPresent(existing -> {
            throw new IllegalStateException(
                "Active order already exists for subscriptionId: " + subscriptionId);
        });

        var order = new Order(UUID.randomUUID(), subscriptionId);
        repository.save(order);
        publisher.publishEvent(new OrderCreatedEvent(order.getId().toString(), subscriptionId));

        return new OrderResponse(order.getId(), order.getSubscriptionId(), order.getStatus(), order.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        var order = repository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        return new OrderResponse(order.getId(), order.getSubscriptionId(), order.getStatus(), order.getCreatedAt());
    }
}
