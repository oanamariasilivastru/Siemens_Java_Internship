package com.siemens.internship;

import com.siemens.internship.model.ItemRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ItemRequest} validation annotations.
 * Verifies that each field-level constraint is enforced by the Jakarta Bean Validation framework.
 */
class ItemRequestValidationTest {

    private static Validator validator; // Validator instance for JSR-380 checks

    /**
     * Set up the Validator once before all tests.
     */
    @BeforeAll
    static void setUpValidator() {
        // Build the default ValidatorFactory
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        // Obtain a Validator from the factory
        validator = factory.getValidator();
    }

    /**
     * When all fields meet their constraints, no violations should be reported.
     */
    @Test
    void whenAllFieldsValid_thenNoViolations() {
        // Given: a valid ItemRequest instance
        ItemRequest req = new ItemRequest(
                "My Item",                       // @NotBlank requirement
                "A perfectly fine description", // @Size within bounds
                "NEW",                           // Pattern matches allowed statuses
                "user@gmail.com"                // Custom @ValidEmail format
        );

        // When: the validator processes this object
        Set<ConstraintViolation<ItemRequest>> violations = validator.validate(req);

        // Then: expect zero constraint violations
        assertTrue(violations.isEmpty(),
                () -> "Expected no violations, but got: " + violations);
    }

    /**
     * When all fields violate their constraints, each violation should be reported.
     */
    @Test
    void whenFieldsInvalid_thenAllViolationsReported() {
        // Given: an ItemRequest with violations on every field
        ItemRequest req = new ItemRequest(
                "",                // Violates @NotBlank on name
                "x".repeat(300),   // Violates @Size(max=255) on description
                "UNKNOWN",         // Violates @Pattern for allowed statuses
                "not-an-email"     // Violates custom @ValidEmail
        );

        // When: the validator runs
        Set<ConstraintViolation<ItemRequest>> violations = validator.validate(req);

        // Then: exactly 4 violations should be found
        assertEquals(4, violations.size(),
                () -> "Expected 4 violations but got: " + violations);

        // And: each property violation should include its property name and message
        assertTrue(violations.stream().anyMatch(v ->
                        "name".equals(v.getPropertyPath().toString()) &&
                                v.getMessage().contains("Name is required")),
                "Expected violation for blank name");

        assertTrue(violations.stream().anyMatch(v ->
                        "description".equals(v.getPropertyPath().toString()) &&
                                v.getMessage().contains("Description cannot exceed")),
                "Expected violation for description length");

        assertTrue(violations.stream().anyMatch(v ->
                        "status".equals(v.getPropertyPath().toString()) &&
                                v.getMessage().contains("Status must be one of")),
                "Expected violation for invalid status");

        assertTrue(violations.stream().anyMatch(v ->
                        "email".equals(v.getPropertyPath().toString()) &&
                                v.getMessage().contains("Email")),
                "Expected violation for invalid email format");
    }
}