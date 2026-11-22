package de.raphaellee.transflow.payment;

import de.raphaellee.transflow.ConflictException;
import de.raphaellee.transflow.order.OrderService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String PROCESSED = "PROCESSED";
    private static final String FAILED = "FAILED";

    private final PaymentRepository repository;
    private final ApplicationEventPublisher publisher;
    private final OrderService orderService;

    PaymentService(PaymentRepository repository, ApplicationEventPublisher publisher, OrderService orderService) {
        this.repository = repository;
        this.publisher = publisher;
        this.orderService = orderService;
    }

    @Transactional
    public PaymentResponse confirmPayment(UUID orderId, PaymentScenario scenario) {
        var order = orderService.getOrder(orderId);
        var existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return repeat(existing.get(), PROCESSED);
        }
        var payment = repository.save(new Payment(orderId, PROCESSED, scenario));
        publisher.publishEvent(new PaymentProcessedEvent(orderId, order.subscriptionId(), scenario));
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse failPayment(UUID orderId) {
        var order = orderService.getOrder(orderId);
        var existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return repeat(existing.get(), FAILED);
        }
        var payment = repository.save(new Payment(orderId, FAILED, null));
        publisher.publishEvent(new PaymentFailedEvent(orderId, order.subscriptionId()));
        return toResponse(payment);
    }

    private PaymentResponse repeat(Payment payment, String requestedStatus) {
        if (!payment.getStatus().equals(requestedStatus)) {
            throw new ConflictException(
                "Payment for order " + payment.getOrderId() + " is already " + payment.getStatus());
        }
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
