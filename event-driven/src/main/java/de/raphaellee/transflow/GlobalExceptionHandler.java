package de.raphaellee.transflow;

import io.grpc.StatusRuntimeException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail notFound(EntityNotFoundException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail conflict(IllegalStateException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail duplicateKey(DataIntegrityViolationException ex) {
        // Catches concurrent duplicate inserts that pass application-level check
        var detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("Resource already exists (concurrent duplicate)");
        return detail;
    }

    @ExceptionHandler(StatusRuntimeException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ProblemDetail temporalUnavailable(StatusRuntimeException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        detail.setDetail("Workflow service temporarily unavailable: " + ex.getStatus().getCode());
        return detail;
    }
}
