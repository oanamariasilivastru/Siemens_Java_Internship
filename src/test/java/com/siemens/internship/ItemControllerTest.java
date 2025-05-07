package com.siemens.internship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siemens.internship.controller.GlobalExceptionHandler;
import com.siemens.internship.controller.ItemController;
import com.siemens.internship.model.Item;
import com.siemens.internship.model.ItemRequest;
import com.siemens.internship.service.ItemService;
import com.siemens.internship.service.ValidEmail;
import com.siemens.internship.service.ValidEmailValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link ItemController}.
 *
 * <p>Verifies all CRUD and asynchronous processing endpoints using MockMvc.
 * Integrates a stubbed email validator and the global exception handler.</p>
 */
class ItemControllerTest {

    @Mock
    private ItemService service; // Mock the service layer

    @InjectMocks
    private ItemController controller; // Controller under test

    private MockMvc mvc; // MockMvc instance for HTTP simulation
    private final ObjectMapper om = new ObjectMapper(); // JSON (de)serializer

    /**
     * Set up MockMvc with controller, exception handler, and custom validator.
     */
    @BeforeEach
    void setup() {
        // Initialize Mockito annotations (@Mock, @InjectMocks)
        MockitoAnnotations.openMocks(this);

        // Create factory to inject stubbed ValidEmailValidator,
        // and instantiate other validators normally
        ConstraintValidatorFactory factory = new ConstraintValidatorFactory() {
            @Override
            public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                if (ValidEmailValidator.class.equals(key)) {
                    // Stubbed validator: always return true for email checks
                    return (T) new ConstraintValidator<ValidEmail, String>() {
                        @Override public void initialize(ValidEmail ann) {}
                        @Override public boolean isValid(String value, ConstraintValidatorContext ctx) {
                            return true; // accept any email format
                        }
                    };
                }
                // Default instantiation for other validators
                try {
                    return key.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create validator: " + key, e);
                }
            }

            @Override
            public void releaseInstance(ConstraintValidator<?, ?> instance) {
                // No cleanup needed
            }
        };

        // Register our custom validator factory
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setConstraintValidatorFactory(factory);
        validator.afterPropertiesSet(); // initialize validator bean

        // Build MockMvc with controller, global exception handling, and validator
        mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    /**
     * GET /api/items should return HTTP 200 and a JSON array of all items.
     */
    @Test
    void getAllReturnsList() throws Exception {
        // Given two sample items in service
        List<Item> items = List.of(
                new Item(1L, "n1", "d1", "NEW", "a@b.com"),
                new Item(2L, "n2", "d2", "NEW", "c@d.com")
        );
        when(service.findAll()).thenReturn(items);

        // When/Then: perform GET and verify response structure and content
        mvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].email").value("c@d.com"));
    }

    /**
     * GET /api/items/{id} for existing item should return HTTP 200 and the item JSON.
     */
    @Test
    void getByIdFound() throws Exception {
        Item item = new Item(1L, "n", "d", "NEW", "x@y.com");
        when(service.findById(1L)).thenReturn(Optional.of(item));

        mvc.perform(get("/api/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("n"));
    }

    /**
     * GET /api/items/{id} when item not found should return HTTP 404 with error payload.
     */
    @Test
    void getByIdNotFound() throws Exception {
        when(service.findById(42L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/items/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.messages[0]").value("Item not found"))
                .andExpect(jsonPath("$.path").value("/api/items/42"));
    }

    /**
     * POST /api/items with valid payload should return HTTP 201 and created item.
     */
    @Test
    void postValidItemReturns201() throws Exception {
        ItemRequest req = new ItemRequest("A", "d", "NEW", "ok@e.com");
        Item saved = new Item(1L, "A", "d", "NEW", "ok@e.com");
        when(service.save(any())).thenReturn(saved);

        mvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    /**
     * POST /api/items with invalid payload triggers validation errors (HTTP 400).
     */
    @Test
    void postValidationError() throws Exception {
        // Prepare invalid request: blank name, too long description, invalid status
        ItemRequest bad = new ItemRequest(
                "",                 // empty name invalid
                "d".repeat(300),    // description exceeds max length
                "BAD",              // status not in enum
                "x"                 // email format stubbed as valid
        );

        mvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages", hasSize(3)));
    }

    /**
     * POST /api/items with duplicate email causes DB constraint violation (HTTP 409).
     */
    @Test
    void postDuplicateEmail() throws Exception {
        ItemRequest req = new ItemRequest("A", "d", "NEW", "dup@e.com");
        when(service.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        mvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Data Conflict"));
    }

    /**
     * PUT /api/items/{id} updates existing item (HTTP 200).
     */
    @Test
    void updateExisting() throws Exception {
        Item existing = new Item(1L, "old", "d", "NEW", "a@b.com");
        ItemRequest req = new ItemRequest("new", "d2", "PROCESSED", "a@b.com");
        Item updated = new Item(1L, "new", "d2", "PROCESSED", "a@b.com");

        when(service.findById(1L)).thenReturn(Optional.of(existing));
        when(service.save(any())).thenReturn(updated);

        mvc.perform(put("/api/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.name").value("new"));
    }

    /**
     * PUT /api/items/{id} for non-existing item returns HTTP 404.
     */
    @Test
    void updateNotFound() throws Exception {
        ItemRequest req = new ItemRequest("n", "d", "NEW", "e@f.com");
        when(service.findById(5L)).thenReturn(Optional.empty());

        mvc.perform(put("/api/items/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.messages[0]").value("Item not found"));
    }

    /**
     * DELETE /api/items/{id} for existing item returns HTTP 204 No Content.
     */
    @Test
    void deleteExists() throws Exception {
        when(service.existsById(3L)).thenReturn(true);
        doNothing().when(service).deleteById(3L);

        mvc.perform(delete("/api/items/3"))
                .andExpect(status().isNoContent());
    }

    /**
     * DELETE /api/items/{id} for non-existing item returns HTTP 404.
     */
    @Test
    void deleteNotFound() throws Exception {
        when(service.existsById(99L)).thenReturn(false);

        mvc.perform(delete("/api/items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.messages[0]").value("Item not found"));
    }

    /**
     * GET /api/items/process triggers async processing and returns processed items (HTTP 200).
     */
    @Test
    void processItemsReturnsList() throws Exception {
        List<Item> processed = List.of(
                new Item(1L, "a", "d", "PROCESSED", "a@b.com")
        );
        // Stub service to return completed future immediately
        when(service.processItemsAsync())
                .thenReturn(CompletableFuture.completedFuture(processed));

        // Perform async request and verify dispatch
        MvcResult result = mvc.perform(get("/api/items/process"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PROCESSED"));
    }
}
