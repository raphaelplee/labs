package de.raphaellee.transflow;

import io.grpc.Status;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @CsvSource({
        "NOT_FOUND, 404",
        "UNAVAILABLE, 503",
        "DEADLINE_EXCEEDED, 503",
        "INVALID_ARGUMENT, 500",
        "INTERNAL, 500"
    })
    void mapsTemporalStatusCodes(Status.Code code, int expectedStatus) {
        var problem = handler.temporal(code.toStatus().asRuntimeException());
        assertThat(problem.getStatus()).isEqualTo(expectedStatus);
    }
}
