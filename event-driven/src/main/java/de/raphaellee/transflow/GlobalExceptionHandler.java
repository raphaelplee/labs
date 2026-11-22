package de.raphaellee.transflow;

import io.grpc.StatusRuntimeException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail notFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(StatusRuntimeException.class)
    ProblemDetail temporal(StatusRuntimeException ex) {
        var code = ex.getStatus().getCode();
        return switch (code) {
            case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "Saga not found");
            case UNAVAILABLE, DEADLINE_EXCEEDED -> {
                log.warn("Temporal call failed: {}", code);
                yield problem(HttpStatus.SERVICE_UNAVAILABLE, "Workflow service temporarily unavailable");
            }
            default -> {
                log.error("Temporal call failed", ex);
                yield problem(HttpStatus.INTERNAL_SERVER_ERROR, "Workflow service error");
            }
        };
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        var problem = ProblemDetail.forStatus(status);
        problem.setDetail(detail);
        return problem;
    }
}
