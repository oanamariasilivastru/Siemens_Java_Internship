package com.siemens.internship;

import com.siemens.internship.service.ValidEmailValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ValidEmailValidator}.
 *
 * <p>Verifies email format checks, MX record lookup, and error handling
 * using mocked JNDI DNS context.</p>
 */
@ExtendWith(MockitoExtension.class)
class ValidEmailValidatorTest {

    private ValidEmailValidator validator;            // Validator under test
    private final ConstraintValidatorContext ctx = null; // Unused context stub

    /**
     * Instantiate a fresh validator before each test.
     */
    @BeforeEach
    void setUp() {
        validator = new ValidEmailValidator();
    }

    /**
     * Null, blank, or malformed addresses should be invalid immediately.
     */
    @Test
    void invalidForNullBlankOrBadFormat() {
        assertFalse(validator.isValid(null, ctx),  "null should be invalid");
        assertFalse(validator.isValid("", ctx),    "empty string should be invalid");
        assertFalse(validator.isValid("   ", ctx), "blank only should be invalid");
        assertFalse(validator.isValid("foo@", ctx),"missing domain part should be invalid");
        assertFalse(validator.isValid("foo@bar", ctx),"missing top-level domain should be invalid");
    }

    /**
     * Valid format with MX record present should return true.
     * Mocks InitialDirContext to simulate an MX record lookup.
     */
    @Test
    void validWhenFormatOkAndMxPresent() throws NamingException {
        // Mock construction of InitialDirContext to control getAttributes
        try (MockedConstruction<InitialDirContext> mc =
                     mockConstruction(InitialDirContext.class, (mockCtx, context) -> {
                         // Prepare Attributes with MX record
                         Attributes attrs = new BasicAttributes();
                         attrs.put("MX", "mx.example.org");
                         // Stub getAttributes to return our MX record set
                         when(mockCtx.getAttributes(anyString(), any(String[].class)))
                                 .thenReturn(attrs);
                     })) {
            // When: validating a properly formatted email
            assertTrue(validator.isValid("user@example.org", ctx));

            // Then: verify getAttributes was called with the domain part
            InitialDirContext instance = mc.constructed().get(0);
            verify(instance).getAttributes("example.org", new String[]{"MX"});
        }
    }

    /**
     * Valid format but no MX record should be considered invalid.
     */
    @Test
    void invalidWhenFormatOkButNoMxRecord() {
        try (MockedConstruction<InitialDirContext> mc =
                     mockConstruction(InitialDirContext.class, (mockCtx, context) -> {
                         // Return empty Attributes (no MX entries)
                         Attributes attrs = new BasicAttributes();
                         when(mockCtx.getAttributes(anyString(), any(String[].class)))
                                 .thenReturn(attrs);
                     })) {
            assertFalse(validator.isValid("user@example.org", ctx),
                    "Valid format with no MX record should be invalid");
        }
    }

    /**
     * If DNS lookup throws a NamingException, isValid should catch it and return false.
     */
    @Test
    void invalidWhenGetAttributesThrowsNamingException() {
        try (MockedConstruction<InitialDirContext> mc =
                     mockConstruction(InitialDirContext.class, (mockCtx, context) -> {
                         // Stub getAttributes to throw NamingException
                         when(mockCtx.getAttributes(anyString(), any(String[].class)))
                                 .thenThrow(new NamingException("DNS failure"));
                     })) {
            assertFalse(validator.isValid("user@example.org", ctx),
                    "DNS errors should result in invalid email");
        }
    }

    /**
     * Verifies that the validator sets JNDI environment correctly
     * and still uses the constructed context for MX lookup.
     */
    @Test
    void hasMxRecordSetsEnvAndUsesIt() {
        // Capture the env Hashtable passed into InitialDirContext
        AtomicReference<Hashtable<String,String>> capturedEnv = new AtomicReference<>();

        try (MockedConstruction<InitialDirContext> mc =
                     mockConstruction(InitialDirContext.class, (mockCtx, context) -> {
                         // Capture constructor argument (JNDI environment)
                         @SuppressWarnings("unchecked")
                         Hashtable<String,String> env = (Hashtable<String,String>) context.arguments().get(0);
                         capturedEnv.set(env);

                         // Provide a valid MX record to satisfy lookup
                         Attributes attrs = new BasicAttributes();
                         attrs.put("MX", "mx.example.org");
                         when(mockCtx.getAttributes(anyString(), any(String[].class)))
                                 .thenReturn(attrs);
                     })) {
            // Perform validation, triggering hasMxRecord logic
            assertTrue(validator.isValid("user@example.com", ctx));

            // Environment must include the DNS context factory
            Hashtable<String,String> env = capturedEnv.get();
            assertNotNull(env, "Expected JNDI environment to be set");
            assertEquals(
                    "com.sun.jndi.dns.DnsContextFactory",
                    env.get("java.naming.factory.initial"),
                    "JNDI factory should be set for DNS lookups"
            );

            // Ensure MX lookup was invoked on the constructed context
            InitialDirContext instance = mc.constructed().get(0);
            verify(instance).getAttributes("example.com", new String[]{"MX"});
        } catch (NamingException e) {
            fail("Unexpected NamingException: " + e.getMessage());
        }
    }
}
