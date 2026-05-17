@org.springframework.modulith.ApplicationModule(
    displayName = "Orchestration",
    allowedDependencies = {"order", "payment", "fulfillment"}
)
package de.raphaellee.transflow.orchestration;
