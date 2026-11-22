package de.raphaellee.transflow.fulfillment;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class FulfillmentService {

    private final FulfillmentRecordRepository repository;
    private final ApplicationEventPublisher publisher;

    FulfillmentService(FulfillmentRecordRepository repository, ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional
    public FulfillmentResponse complete(UUID orderId, UUID subscriptionId) {
        var existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        var record = repository.save(new FulfillmentRecord(orderId, subscriptionId));
        publisher.publishEvent(new FulfillmentCompletedEvent(record.getId(), orderId, subscriptionId));
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public FulfillmentResponse getByOrderId(UUID orderId) {
        var record = repository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Fulfillment not found for order: " + orderId));
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public List<FulfillmentResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse).getContent();
    }

    private FulfillmentResponse toResponse(FulfillmentRecord r) {
        return new FulfillmentResponse(r.getId(), r.getOrderId(), r.getSubscriptionId(), r.getStatus(), r.getFulfilledAt());
    }
}
