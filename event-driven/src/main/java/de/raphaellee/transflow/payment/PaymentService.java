package de.raphaellee.transflow.payment;

import de.raphaellee.transflow.order.OrderService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String DEFAULT_SCENARIO = "happy-path";

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
        // Note: orderService.getOrder() joins this transaction (REQUIRED propagation);
        // its readOnly hint is discarded — this outer transaction is read-write.
        var order = orderService.getOrder(orderId);
        var payment = new Payment(orderId, "PROCESSED", scenario);
        repository.save(payment);
        publisher.publishEvent(new PaymentProcessedEvent(
            orderId.toString(), order.subscriptionId(),
            scenario != null ? scenario : DEFAULT_SCENARIO));
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getStatus());
    }

    @Transactional
    public PaymentResponse failPayment(UUID orderId) {
        // Note: orderService.getOrder() joins this transaction (REQUIRED propagation);
        // its readOnly hint is discarded — this outer transaction is read-write.
        var order = orderService.getOrder(orderId);
        var payment = new Payment(orderId, "FAILED", null);
        repository.save(payment);
        publisher.publishEvent(new PaymentFailedEvent(orderId.toString(), order.subscriptionId()));
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
