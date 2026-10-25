package de.raphaellee.transflow.orchestration;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.common.converter.DataConverter;
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

        String status = internalStatus != null ? internalStatus : mapStatus(info.getStatus());
        Instant startedAt = Instant.ofEpochSecond(
            info.getStartTime().getSeconds(), info.getStartTime().getNanos());
        Instant closedAt = info.hasCloseTime()
            ? Instant.ofEpochSecond(info.getCloseTime().getSeconds(), info.getCloseTime().getNanos())
            : null;

        UUID orderId = extractMemoUUID(info, "orderId");
        return new SagaStatus(workflowId, subscriptionId, status, orderId, startedAt, closedAt, deriveSteps(status));
    }

    private UUID extractMemoUUID(WorkflowExecutionInfo info, String key) {
        var fields = info.getMemo().getFieldsMap();
        if (!fields.containsKey(key)) return null;
        try {
            String value = DataConverter.getDefaultInstance()
                .fromPayload(fields.get(key), String.class, String.class);
            return UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String mapStatus(WorkflowExecutionStatus temporalStatus) {
        return switch (temporalStatus) {
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
