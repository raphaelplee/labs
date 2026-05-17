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

    SagaStatus fromExecutionInfo(WorkflowExecutionInfo info) {
        String workflowId = info.getExecution().getWorkflowId();
        UUID subscriptionId = workflowId.startsWith("saga-")
            ? UUID.fromString(workflowId.substring(5))
            : UUID.fromString(workflowId);

        String status = mapStatus(info.getStatus());
        Instant startedAt = Instant.ofEpochSecond(
            info.getStartTime().getSeconds(), info.getStartTime().getNanos());
        Instant updatedAt = info.hasCloseTime()
            ? Instant.ofEpochSecond(info.getCloseTime().getSeconds(), info.getCloseTime().getNanos())
            : Instant.now();

        return new SagaStatus(workflowId, subscriptionId, status, null,
            startedAt, updatedAt, deriveSteps(status), null);
    }

    SagaStatus fromDescribeResponse(DescribeWorkflowExecutionResponse resp) {
        var info = resp.getWorkflowExecutionInfo();
        String workflowId = info.getExecution().getWorkflowId();
        UUID subscriptionId = workflowId.startsWith("saga-")
            ? UUID.fromString(workflowId.substring(5))
            : UUID.fromString(workflowId);

        String status = mapStatus(info.getStatus());
        Instant startedAt = Instant.ofEpochSecond(
            info.getStartTime().getSeconds(), info.getStartTime().getNanos());
        Instant updatedAt = info.hasCloseTime()
            ? Instant.ofEpochSecond(info.getCloseTime().getSeconds(), info.getCloseTime().getNanos())
            : Instant.now();

        return new SagaStatus(workflowId, subscriptionId, status, null,
            startedAt, updatedAt, deriveSteps(status), null);
    }

    private String mapStatus(WorkflowExecutionStatus temporalStatus) {
        return switch (temporalStatus) {
            case WORKFLOW_EXECUTION_STATUS_RUNNING -> "AWAITING_PAYMENT";
            case WORKFLOW_EXECUTION_STATUS_COMPLETED -> "COMPLETED";
            case WORKFLOW_EXECUTION_STATUS_FAILED -> "PAYMENT_FAILED";
            case WORKFLOW_EXECUTION_STATUS_TIMED_OUT -> "TIMED_OUT";
            case WORKFLOW_EXECUTION_STATUS_CANCELED -> "PAYMENT_FAILED";
            default -> temporalStatus.name();
        };
    }

    private List<SagaStep> deriveSteps(String status) {
        return switch (status) {
            case "AWAITING_PAYMENT" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED", null),
                new SagaStep("AWAITING_PAYMENT", "RUNNING", null)
            );
            case "FULFILLMENT_PROCESSING" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED", null),
                new SagaStep("AWAITING_PAYMENT", "COMPLETED", null),
                new SagaStep("FULFILLMENT_PROCESSING", "RUNNING", null)
            );
            case "COMPLETED" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED", null),
                new SagaStep("AWAITING_PAYMENT", "COMPLETED", null),
                new SagaStep("FULFILLMENT_PROCESSING", "COMPLETED", null)
            );
            case "PAYMENT_FAILED" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED", null),
                new SagaStep("PAYMENT_FAILED", "FAILED", null)
            );
            case "TIMED_OUT" -> List.of(
                new SagaStep("ORDER_CREATED", "COMPLETED", null),
                new SagaStep("AWAITING_PAYMENT", "COMPLETED", null),
                new SagaStep("FULFILLMENT_PROCESSING", "TIMED_OUT", null)
            );
            default -> List.of();
        };
    }
}
