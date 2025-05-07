package com.siemens.internship.model;

import com.siemens.internship.service.ValidEmail;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for creating or updating an Item.
 * Validation annotations ensure incoming JSON is well-formed
 * before reaching business logic.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ItemRequest {

    /** Must not be null, empty, or only whitespace. */
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed {max} characters")
    private String name;

    /** Optional description, up to 255 characters. */
    @Size(max = 255, message = "Description cannot exceed {max} characters")
    private String description;

    /** Must be one of the predefined statuses; cannot be null. */
    @NotNull(message = "Status is required")
    @Pattern(regexp = "NEW|PROCESSED|CANCELLED",
            message = "Status must be one of NEW, PROCESSED, CANCELLED")
    private String status;

    /** Must be a deliverable email address; cannot be blank. */
    @NotBlank(message = "Email is required")
    @ValidEmail
    private String email;
}
