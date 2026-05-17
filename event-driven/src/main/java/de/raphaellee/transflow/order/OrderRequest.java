package de.raphaellee.transflow.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrderRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$", message = "subscriptionId must be 1-128 alphanumeric, hyphen, or underscore characters")
        String subscriptionId) {}
