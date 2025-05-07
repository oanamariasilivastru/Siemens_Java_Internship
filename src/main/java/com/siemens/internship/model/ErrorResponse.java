package com.siemens.internship.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable envelope for all error responses returned by the API.
 * Fields:
 *   {@code timestamp} – when the error occurred (ISO-8601 with offset)
 *   {@code status} – HTTP status code
 *   {@code error} – standard HTTP reason phrase
 *   {@code messages} – detailed validation or error messages
 *   {@code path} – the request URI that triggered the error
 *
 */
@Builder
public record ErrorResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime timestamp,
        int            status,
        String         error,
        List<String>   messages,
        String         path
) {
    /**
     * Convenience factory that populates the timestamp to now.
     *
     * @param status   HTTP status code
     * @param error    HTTP reason (e.g. “Bad Request”)
     * @param messages list of human-readable error details
     * @param path     the URI path of the request
     * @return new ErrorResponse instance
     */
    public static ErrorResponse of(
            int status, String error, List<String> messages, String path) {

        return ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status)
                .error(error)
                .messages(messages)
                .path(path)
                .build();
    }
}
