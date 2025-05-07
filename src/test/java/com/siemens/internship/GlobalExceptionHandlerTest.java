package com.siemens.internship;

import com.siemens.internship.controller.GlobalExceptionHandler;
import com.siemens.internship.model.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Verifies that each exception scenario produces the correct HTTP status,
 * error message, and detailed response body.</p>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    /**
     * Initialize a fresh handler before each test.
     */
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    /**
     * Test handling of MethodArgumentNotValidException with multiple field errors.
     */
    @Test
    void handleMethodArgumentNotValid() {
        // Mock two field errors in the request body
        FieldError fe1 = new FieldError("obj", "field1", "must not be blank");
        FieldError fe2 = new FieldError("obj", "field2", "must be numeric");
        BindingResult binding = mock(BindingResult.class);
        when(binding.getFieldErrors()).thenReturn(List.of(fe1, fe2));

        // Create exception and mock request context
        var ex = new MethodArgumentNotValidException(null, binding);
        var req = new ServletWebRequest(new MockHttpServletRequest("POST", "/test"));

        // Invoke handler
        var responseEntity = handler.handleMethodArgumentNotValid(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, req);
        var body = (ErrorResponse) responseEntity.getBody();

        // Verify status and response content
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals("Validation Failed", body.error());
        assertEquals(
                List.of("field1: must not be blank", "field2: must be numeric"),
                body.messages()
        );
        assertEquals("/test", body.path());
        assertNotNull(body.timestamp(), "Timestamp should be set");
    }

    /**
     * Test handling of ConstraintViolationException with no violations.
     */
    @Test
    void handleConstraintViolationsEmpty() {
        var ex = new ConstraintViolationException(Set.of());
        var req = new ServletWebRequest(new MockHttpServletRequest("GET", "/foo"));

        var responseEntity = handler.handleConstraintViolations(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals("Constraint Violation", body.error());
        assertTrue(body.messages().isEmpty(), "Expected no error messages");
        assertEquals("/foo", body.path());
    }

    /**
     * Test handling of ConstraintViolationException with a single violation.
     */
    @Test
    void handleConstraintViolationsSingle() {
        // Mock a single constraint violation
        ConstraintViolation<Object> cv = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("param1");
        when(cv.getPropertyPath()).thenReturn(path);
        when(cv.getMessage()).thenReturn("must be positive");

        var ex = new ConstraintViolationException(Set.of(cv));
        var req = new ServletWebRequest(new MockHttpServletRequest("POST", "/bar"));

        var responseEntity = handler.handleConstraintViolations(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals("Constraint Violation", body.error());
        assertEquals(
                List.of("param1: must be positive"),
                body.messages()
        );
        assertEquals("/bar", body.path());
    }

    /**
     * Test handling of MethodArgumentTypeMismatchException with a known target type.
     */
    @Test
    void handleTypeMismatch() {
        var ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, null
        );
        var req = new ServletWebRequest(new MockHttpServletRequest("GET", "/baz"));

        var responseEntity = handler.handleTypeMismatch(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals("Type Mismatch", body.error());
        assertTrue(body.messages().get(0).contains("expected Long"));
        assertEquals("/baz", body.path());
    }

    /**
     * Test handling of MethodArgumentTypeMismatchException when target type is unknown.
     */
    @Test
    void handleTypeMismatchWithUnknown() {
        var ex = new MethodArgumentTypeMismatchException(
                "xyz", null, "param", null, null
        );
        var req = new ServletWebRequest(new MockHttpServletRequest("GET", "/foo"));

        var responseEntity = handler.handleTypeMismatch(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals("Type Mismatch", body.error());
        assertTrue(body.messages().get(0).contains("expected unknown"));
        assertEquals("/foo", body.path());
    }

    /**
     * Test handling of DataIntegrityViolationException with a nested cause.
     */
    @Test
    void handleIntegrity() {
        var rootCause = new RuntimeException("rootCause");
        var ex = new DataIntegrityViolationException("wrapper", rootCause);
        var req = new ServletWebRequest(new MockHttpServletRequest("PUT", "/conflict"));

        var responseEntity = handler.handleIntegrity(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.CONFLICT, responseEntity.getStatusCode());
        assertEquals("Data Conflict", body.error());
        assertEquals(
                List.of("rootCause"),
                body.messages()
        );
        assertEquals("/conflict", body.path());
    }

    /**
     * Test handling of ResponseStatusException with a custom reason message.
     */
    @Test
    void handleResponseStatusWithReason() {
        var ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "not here");
        var req = new ServletWebRequest(new MockHttpServletRequest("DELETE", "/items/1"));

        var responseEntity = handler.handleResponseStatus(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertEquals("Not Found", body.error());
        assertEquals(
                List.of("not here"),
                body.messages()
        );
        assertEquals("/items/1", body.path());
    }

    /**
     * Test handling of ResponseStatusException without a custom reason (falls back to default).
     */
    @Test
    void handleResponseStatusWithoutReason() {
        var ex = new ResponseStatusException(HttpStatus.FORBIDDEN, null);
        var req = new ServletWebRequest(new MockHttpServletRequest("DELETE", "/secret"));

        var responseEntity = handler.handleResponseStatus(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.FORBIDDEN, responseEntity.getStatusCode());
        assertEquals("Forbidden", body.error());
        assertEquals(
                List.of("Forbidden"),
                body.messages()
        );
        assertEquals("/secret", body.path());
    }

    /**
     * Test handling of ResponseStatusException with a non-standard HTTP code.
     */
    @Test
    void handleResponseStatusExceptionWithCustomCode() {
        HttpStatusCode customCode = HttpStatusCode.valueOf(499);
        var ex = new ResponseStatusException(customCode, "some reason");
        var req = new ServletWebRequest(new MockHttpServletRequest("GET", "/custom"));

        var responseEntity = handler.handleResponseStatus(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(499, body.status());
        assertEquals(customCode.toString(), body.error());
        assertEquals(
                List.of("some reason"),
                body.messages()
        );
        assertEquals("/custom", body.path());
    }

    /**
     * Test handling of an uncaught exception (fallback to 500 Internal Server Error).
     */
    @Test
    void handleAllExceptions() {
        var ex = new IllegalStateException("boom");
        var req = new ServletWebRequest(new MockHttpServletRequest("PATCH", "/anything"));

        var responseEntity = handler.handleAll(ex, req);
        var body = (ErrorResponse) responseEntity.getBody();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        assertEquals("Internal Server Error", body.error());
        assertEquals(
                List.of("boom"),
                body.messages()
        );
        assertEquals("/anything", body.path());
    }
}
