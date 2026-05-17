package de.raphaellee.transflow.payment;

import de.raphaellee.transflow.order.OrderService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final ApplicationEventPublisher publisher;
    private final OrderService orderService;

    PaymentService(PaymentRepository repository, ApplicationEventPublisher publisher, OrderService orderService) {
        this.repository = repository;
        this.publisher = publisher;
        this.orderService = orderService;
    }

    @Transactional
    public PaymentResponse confirmPayment(UUID orderId, String scenario) {
        var order = orderService.getOrder(orderId); // throws EntityNotFoundException if not found
        var payment = new Payment(orderId, "PROCESSED", scenario);
        repository.save(payment);
        publisher.publishEvent(new PaymentProcessedEvent(
            orderId.toString(), order.subscriptionId(),
            scenario != null ? scenario : "happy-path"));
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getStatus());
    }

    @Transactional
    public PaymentResponse failPayment(UUID orderId) {
        var order = orderService.getOrder(orderId); // throws EntityNotFoundException if not found
        var payment = new Payment(orderId, "FAILED", null);
        repository.save(payment);
        publisher.publishEvent(new PaymentFailedEvent(orderId.toString(), order.subscriptionId()));
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
