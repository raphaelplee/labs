package de.raphaellee.transflow.orchestration;

import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.WorkflowReplayer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Tag("unit")
class SubscriptionSagaWorkflowReplayTest {

    @ParameterizedTest
    @ValueSource(strings = {"awaiting-payment.json", "completed.json"})
    void historiesFromBeforeThePaymentTimeoutStillReplay(String file) throws Exception {
        WorkflowReplayer.replayWorkflowExecution(history(file), SubscriptionSagaWorkflowImpl.class);
    }

    private static WorkflowExecutionHistory history(String file) throws IOException {
        try (var in = SubscriptionSagaWorkflowReplayTest.class.getResourceAsStream("/histories/" + file)) {
            return WorkflowExecutionHistory.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
