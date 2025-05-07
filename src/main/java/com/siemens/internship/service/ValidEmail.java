package com.siemens.internship.service;

import jakarta.validation.*;
import java.lang.annotation.*;

/**
 * Custom constraint annotation for validating "deliverable" email addresses.
 *
 * Ensures the annotated String:
 *
 *   Conforms to a basic RFC‑style email format (user@domain.tld).
 *   The domain portion has at least one DNS MX record.
 *
 *
 * Use on fields or method parameters to enforce deliverable email validation.
 */
@Documented
@Constraint(validatedBy = ValidEmailValidator.class) // Specifies the validator implementation
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

    /**
     * Default validation failure message.
     *
     * Can be overridden in the annotation usage or via resource bundles.
     */
    String message() default "Email address is not deliverable";

    /**
     * Allows the specification of validation groups to which this constraint belongs.
     *
     * Groups can be used to control the execution order or conditional validation.
     */
    Class<?>[] groups() default {};

    /**
     * Can be used by clients of the Jakarta Bean Validation API to assign custom payload objects
     * to a constraint. This is typically not used directly by the application logic.
     */
    Class<? extends Payload>[] payload() default {};
}
