package de.raphaellee.transflow.orchestration;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class SagaStatusMapper {

    SagaStatus fromExecutionInfo(WorkflowExecutionInfo info, String internalStatus) {
        return toSagaStatus(info, internalStatus);
    }

    SagaStatus fromDescribeResponse(DescribeWorkflowExecutionResponse resp, String internalStatus) {
        return toSagaStatus(resp.getWorkflowExecutionInfo(), internalStatus);
    }

    private SagaStatus toSagaStatus(WorkflowExecutionInfo info, String internalStatus) {
        String workflowId = info.getExecution().getWorkflowId();
        UUID subscriptionId = workflowId.startsWith("saga-")
            ? UUID.fromString(workflowId.substring(5))
            : UUID.fromString(workflowId);

        // Prefer the queried internal status (accurate for all terminal states).
        // The Temporal-level status cannot distinguish PAYMENT_FAILED from COMPLETED —
        // both end with WORKFLOW_EXECUTION_STATUS_COMPLETED because the workflow returns normally.
        String status = internalStatus != null ? internalStatus : mapStatus(info.getStatus());
        Instant startedAt = Instant.ofEpochSecond(
            info.getStartTime().getSeconds(), info.getStartTime().getNanos());
        // closedAt is null for running workflows — Instant.now() would differ on every call
        Instant closedAt = info.hasCloseTime()
            ? Instant.ofEpochSecond(info.getCloseTime().getSeconds(), info.getCloseTime().getNanos())
            : null;

        return new SagaStatus(workflowId, subscriptionId, status, startedAt, closedAt, deriveSteps(status));
    }

    private String mapStatus(WorkflowExecutionStatus temporalStatus) {
        return switch (temporalStatus) {
            // RUNNING means the workflow is open; the internal saga step cannot be determined
            // from Temporal's external status alone — a query call would be needed for precision.
            case WORKFLOW_EXECUTION_STATUS_RUNNING -> "RUNNING";
            case WORKFLOW_EXECUTION_STATUS_COMPLETED -> "COMPLETED";
            case WORKFLOW_EXECUTION_STATUS_FAILED -> "FAILED";
            case WORKFLOW_EXECUTION_STATUS_TIMED_OUT -> "TIMED_OUT";
            case WORKFLOW_EXECUTION_STATUS_CANCELED -> "CANCELED";
            default -> temporalStatus.name();
        };
    }

    private List<SagaStep> deriveSteps(String status) {
        return switch (status) {
            // Internal saga states (from getStatus() query — precise)
            case "AWAITING_PAYMENT" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("AWAITING_PAYMENT", "RUNNING")
            );
            case "FULFILLMENT_PROCESSING" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("AWAITING_PAYMENT", "COMPLETED"),
                new SagaStep("FULFILLMENT_PROCESSING", "RUNNING")
            );
            case "PAYMENT_FAILED" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("PAYMENT_FAILED", "FAILED")
            );
            case "TIMED_OUT" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("AWAITING_PAYMENT", "COMPLETED"),
                new SagaStep("FULFILLMENT_PROCESSING", "TIMED_OUT")
            );
            case "COMPLETED" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("AWAITING_PAYMENT", "COMPLETED"),
                new SagaStep("FULFILLMENT_PROCESSING", "COMPLETED")
            );
            // Temporal-level fallbacks (when getStatus() query is unavailable)
            case "RUNNING" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("IN_PROGRESS", "RUNNING")
            );
            case "FAILED" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("PAYMENT_FAILED", "FAILED")
            );
            case "CANCELED" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED"),
                new SagaStep("CANCELED", "CANCELED")
            );
            default -> List.of();
        };
    }
}
