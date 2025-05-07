package com.siemens.internship.controller;

import com.siemens.internship.model.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception handler that intercepts all common errors,
 * transforms them into {@link ErrorResponse}, and returns consistent JSON.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handle validation errors on {@code @Valid} request bodies.
     */
    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        // Extract each field error as "field: message"
        List<String> errs = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        return build(status, "Validation Failed", errs, request);
    }

    /**
     * Handle violations on {@code @PathVariable} or {@code @RequestParam}
     * when using {@code @Validated} at the controller level.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolations(
            ConstraintViolationException ex,
            WebRequest request) {

        List<String> errs = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.toList());

        return build(HttpStatus.BAD_REQUEST, "Constraint Violation", errs, request);
    }

    /**
     * Handle type mismatches, e.g. invalid ID format in URI.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request) {

        String expected = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "unknown";
        String msg = ex.getName() + ": expected " + expected;
        return build(HttpStatus.BAD_REQUEST, "Type Mismatch", List.of(msg), request);
    }

    /**
     * Handle database integrity violations (e.g. unique constraint).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(
            DataIntegrityViolationException ex,
            WebRequest request) {

        // Return the root cause message (could be DB-specific)
        String cause = ex.getMostSpecificCause().getMessage();
        return build(HttpStatus.CONFLICT, "Data Conflict", List.of(cause), request);
    }

    /**
     * Handle explicit {@link ResponseStatusException} thrown in controllers.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(
            ResponseStatusException ex,
            WebRequest request) {

        HttpStatusCode sc = ex.getStatusCode();
        // Derive reason phrase if possible
        String err = (sc instanceof HttpStatus hs)
                ? hs.getReasonPhrase()
                : sc.toString();

        // Use the exception reason if present, else repeat the HTTP reason
        String detail = ex.getReason() != null ? ex.getReason() : err;
        return build(sc, err, List.of(detail), request);
    }

    /**
     * Catch-all for any other uncaught exceptions.
     * Logs stacktrace, but exposes only message to client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAll(
            Exception ex,
            WebRequest request) {

        log.error("Unhandled exception caught in GlobalExceptionHandler", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                List.of(ex.getMessage()),
                request
        );
    }

    /**
     * Helper to assemble and return an {@link ErrorResponse}.
     */
    private ResponseEntity<Object> build(
            HttpStatusCode status,
            String error,
            List<String> messages,
            WebRequest req) {

        // Strip leading "uri=" from description to get the raw path
        String path = req.getDescription(false).replaceFirst("^uri=", "");
        ErrorResponse body = ErrorResponse.of(
                status.value(), error, messages, path
        );
        return ResponseEntity.status(status).body(body);
    }
}
