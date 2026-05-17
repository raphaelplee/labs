package de.raphaellee.transflow.orchestration;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TemporalConfig {

    @Value("${temporal.address:localhost:7233}")
    private String temporalAddress;

    @Value("${temporal.namespace:default}")
    private String namespace;

    @Value("${temporal.task-queue:subscription-saga-queue}")
    private String taskQueue;

    @Bean
    WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalAddress)
                .build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build());
    }

    @Bean
    WorkerFactory workerFactory(WorkflowClient client) {
        var factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(SubscriptionSagaWorkflowImpl.class);
        factory.start();
        return factory;
    }
}
