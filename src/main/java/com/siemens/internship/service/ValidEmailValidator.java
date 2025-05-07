package com.siemens.internship.service;

import com.siemens.internship.service.ValidEmail;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.regex.Pattern;

/**
 * Validator for verifying email addresses.
 *
 * Performs both a regex format check and a DNS MX record lookup.
 * Implements the Jakarta Bean Validation {@link ConstraintValidator} interface.
 */
public class ValidEmailValidator implements ConstraintValidator<ValidEmail, String> {

    /**
     * Regex pattern for basic email format validation.
     *
     * Slightly stricter than the default Hibernate @Email pattern:
     * - Allows alphanumeric characters, dots, underscores, percent, plus, and hyphens before '@'.
     * - Allows domain labels with alphanumeric characters and hyphens.
     * - Requires a top-level domain of at least two letters.
     */
    private static final Pattern FORMAT = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Validates the provided email address.
     *
     * Returns false if the value is null, blank, or fails the format regex.
     * Otherwise, performs a DNS MX record lookup on the domain portion.
     *
     * @param value the email address to validate
     * @param ctx   the validation context (unused)
     * @return true if format is valid and an MX record exists, false otherwise
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) {
            // Reject null or empty email addresses
            return false;
        }
        if (!FORMAT.matcher(value).matches()) {
            // Reject if regex pattern does not match
            return false;
        }

        // Extract domain part after '@'
        String domain = value.substring(value.indexOf('@') + 1);
        // Check DNS MX record for domain
        return hasMxRecord(domain);
    }

    /**
     * Checks for the presence of an MX record for the given domain.
     *
     * Uses JNDI to perform a DNS lookup:
     * - Configures the JNDI environment to use the DNS context factory.
     * - Queries for MX records via {@link DirContext#getAttributes}.
     *
     * @param domain the DNS domain to query
     * @return true if at least one MX record is found, false on error or absence
     */
    private boolean hasMxRecord(String domain) {
        try {
            // Set up JNDI environment for DNS lookups
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial",
                    "com.sun.jndi.dns.DnsContextFactory");

            // Create a DNS context using the specified environment
            DirContext dns = new InitialDirContext(env);

            // Query for MX records; returns an Attributes object
            Attributes attrs = dns.getAttributes(domain, new String[]{"MX"});

            // If the 'MX' attribute is present, the domain has mail servers
            return attrs.get("MX") != null;
        } catch (NamingException ex) {
            // Any DNS resolution error or absence of MX record results in invalid email
            return false;
        }
    }
}
