package de.raphaellee.transflow.orchestration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/sagas")
@Tag(name = "Sagas", description = "Query saga status via Temporal Visibility API")
class SagaController {

    private static final Logger log = LoggerFactory.getLogger(SagaController.class);

    private final WorkflowClient workflowClient;
    private final WorkflowServiceStubs stubs;
    private final SagaStatusMapper mapper;

    @Value("${spring.temporal.namespace:default}")
    private String namespace;

    SagaController(WorkflowClient workflowClient, SagaStatusMapper mapper) {
        this.workflowClient = workflowClient;
        this.stubs = workflowClient.getWorkflowServiceStubs();
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List all sagas — uses Temporal advanced visibility (Elasticsearch)")
    @ApiResponse(responseCode = "200", description = "Saga list returned")
    @ApiResponse(responseCode = "503", description = "Temporal service unavailable")
    public ResponseEntity<List<SagaStatus>> listSagas() {
        var request = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(namespace)
            .setQuery("WorkflowType = 'SubscriptionSagaWorkflow'")
            .setPageSize(50)
            .build();

        var response = stubs.blockingStub().listWorkflowExecutions(request);

        var sagas = response.getExecutionsList().stream()
            .map(info -> mapper.fromExecutionInfo(info,
                querySagaStatus(info.getExecution().getWorkflowId(), info.getExecution().getRunId())))
            .sorted(java.util.Comparator.comparing(SagaStatus::startedAt,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .toList();

        return ResponseEntity.ok(sagas);
    }

    @GetMapping("/{sagaId}")
    @Operation(summary = "Get saga detail by workflowId")
    @ApiResponse(responseCode = "200", description = "Saga found")
    @ApiResponse(responseCode = "503", description = "Temporal service unavailable")
    public ResponseEntity<SagaStatus> getSaga(
            @Parameter(description = "Temporal workflow ID, e.g. saga-{subscriptionId}")
            @PathVariable String sagaId) {
        var request = DescribeWorkflowExecutionRequest.newBuilder()
            .setNamespace(namespace)
            .setExecution(WorkflowExecution.newBuilder()
                .setWorkflowId(sagaId)
                .build())
            .build();

        var response = stubs.blockingStub().describeWorkflowExecution(request);
        var runId = response.getWorkflowExecutionInfo().getExecution().getRunId();
        return ResponseEntity.ok(mapper.fromDescribeResponse(response, querySagaStatus(sagaId, runId)));
    }

    /**
     * Queries the workflow's internal getStatus() @QueryMethod. Works for both running and
     * closed (completed/failed) workflows — Temporal replays history to reconstruct the state.
     * Returns null if the query fails; callers fall back to the Temporal-level status.
     */
    private String querySagaStatus(String workflowId, String runId) {
        try {
            return workflowClient
                .newUntypedWorkflowStub(workflowId, Optional.of(runId), Optional.empty())
                .query("getStatus", String.class);
        } catch (Exception e) {
            log.warn("getStatus query failed for {} — falling back to Temporal status: {}", workflowId, e.getMessage());
            return null;
        }
    }
}
