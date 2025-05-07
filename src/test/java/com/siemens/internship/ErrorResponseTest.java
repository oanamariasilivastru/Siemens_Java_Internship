package com.siemens.internship;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.siemens.internship.model.ErrorResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ErrorResponse record, including:
 * - Builder behavior
 * - Factory method correctness
 * - JSON serialization format
 */
class ErrorResponseTest {

    private static ObjectMapper objectMapper;

    /**
     * Initializes the ObjectMapper before any test runs.
     * Configures support for Java 8 time types and disables timestamp output.
     */
    @BeforeAll
    static void initMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Support for OffsetDateTime
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Tests that the builder correctly copies all fields.
     * Ensures values are retained as provided.
     */
    @Test
    void builderCopiesAllFields() {
        OffsetDateTime ts = OffsetDateTime.parse("2025-05-07T10:15:30+03:00");
        List<String> msgs = List.of("first", "second");

        ErrorResponse er = ErrorResponse.builder()
                .timestamp(ts)
                .status(418)
                .error("I'm a teapot")
                .messages(msgs)
                .path("/brew-coffee")
                .build();

        assertAll(
                () -> assertEquals(ts, er.timestamp(), "Timestamp should match input"),
                () -> assertEquals(418, er.status(), "Status code should match input"),
                () -> assertEquals("I'm a teapot", er.error(), "Error message should match input"),
                () -> assertSame(msgs, er.messages(), "Messages list should be same reference"),
                () -> assertEquals("/brew-coffee", er.path(), "Path should match input")
        );
    }

    /**
     * Verifies that the static factory method 'of' sets the timestamp automatically
     * and copies all other fields as provided.
     */
    @Test
    void ofSetsTimestampAndFieldsCorrectly() {
        List<String> details = List.of("detail1");

        ErrorResponse er = ErrorResponse.of(404, "Not Found", details, "/missing");

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime actual = er.timestamp();

        // Check timestamp is very close to current time
        assertTrue(
                !actual.isBefore(now.minusSeconds(2)) && !actual.isAfter(now.plusSeconds(2)),
                "Timestamp should be close to current time"
        );

        assertEquals(404, er.status());
        assertEquals("Not Found", er.error());
        assertEquals(details, er.messages());
        assertEquals("/missing", er.path());
    }

    /**
     * Verifies that JSON serialization of ErrorResponse uses ISO 8601 format
     * with offset (e.g., 2025-05-07T12:34:56+03:00) for the timestamp field.
     */
    @Test
    void jsonSerializationRespectsIso8601WithOffset() throws Exception {
        OffsetDateTime ts = OffsetDateTime.parse("2025-05-07T12:34:56+03:00");

        ErrorResponse er = ErrorResponse.builder()
                .timestamp(ts)
                .status(500)
                .error("Oops")
                .messages(List.of())
                .path("/fail")
                .build();

        String json = objectMapper.writeValueAsString(er);
        JsonNode node = objectMapper.readTree(json);

        assertEquals(500, node.get("status").intValue());
        assertEquals("Oops", node.get("error").textValue());
        assertEquals("/fail", node.get("path").textValue());
        assertTrue(node.get("messages").isArray());

        // Check timestamp format
        String tsText = node.get("timestamp").textValue();
        Pattern isoWithOffset = Pattern.compile(
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}"
        );

        assertTrue(isoWithOffset.matcher(tsText).matches(),
                "Timestamp should match ISO-8601 format with timezone offset");

        assertEquals("2025-05-07T12:34:56+03:00", tsText);
    }
}
