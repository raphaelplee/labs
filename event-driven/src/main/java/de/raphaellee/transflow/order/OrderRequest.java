package de.raphaellee.transflow.order;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderRequest(@NotNull UUID subscriptionId) {}
