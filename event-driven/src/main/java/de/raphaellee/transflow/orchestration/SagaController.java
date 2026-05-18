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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/sagas")
@Tag(name = "Sagas", description = "Query saga status via Temporal Visibility API")
class SagaController {

    private final WorkflowServiceStubs stubs;
    private final SagaStatusMapper mapper;

    @Value("${spring.temporal.namespace:default}")
    private String namespace;

    SagaController(WorkflowClient workflowClient, SagaStatusMapper mapper) {
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
            .setQuery("WorkflowType = 'SubscriptionSagaWorkflow' ORDER BY StartTime DESC")
            .setPageSize(50)
            .build();

        var response = stubs.blockingStub().listWorkflowExecutions(request);

        var sagas = response.getExecutionsList().stream()
            .map(mapper::fromExecutionInfo)
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
        return ResponseEntity.ok(mapper.fromDescribeResponse(response));
    }
}
