package de.raphaellee.transflow.payment;

/**
 * Allowed payment confirmation scenarios for saga simulation.
 * Spring MVC converts the request parameter by name (case-insensitive via StringToEnumConverterFactory).
 * Jackson serialises to/from the enum constant name on the Kafka wire.
 */
public enum PaymentScenario {
    HAPPY_PATH,
    FULFILLMENT_TIMEOUT
}
