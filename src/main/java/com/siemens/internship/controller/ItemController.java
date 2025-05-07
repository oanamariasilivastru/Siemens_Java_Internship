package com.siemens.internship.controller;

import com.siemens.internship.model.Item;
import com.siemens.internship.model.ItemRequest;
import com.siemens.internship.service.ItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller exposing CRUD and asynchronous processing endpoints for {@link Item} entities.
 *
 * Delegates business logic to {@link ItemService} and handles HTTP request/response mapping.
 */
@RestController
@RequestMapping("/api/items")
@Validated  // enables validation on @PathVariable parameters
public class ItemController {

    private final ItemService service;

    /**
     * Constructor injection of the service.
     *
     * @param service the ItemService to delegate business operations to
     */
    public ItemController(ItemService service) {
        this.service = service;
    }

    /**
     * Convert {@link ItemRequest} DTO to {@link Item} entity.
     *
     * @param req the incoming request DTO containing item data
     * @return a new Item entity populated with values from the DTO
     */
    private static Item toEntity(ItemRequest req) {
        Item item = new Item();
        item.setName(req.getName());
        item.setDescription(req.getDescription());
        item.setStatus(req.getStatus());
        item.setEmail(req.getEmail());
        return item;
    }

    /**
     * Retrieve all items.
     *
     * @return ResponseEntity containing list of all items and HTTP status 200 OK
     */
    @GetMapping
    public ResponseEntity<List<Item>> getAll() {
        List<Item> items = service.findAll();
        return ResponseEntity.ok(items);
    }

    /**
     * Create a new item from the provided DTO.
     *
     * @param req the ItemRequest DTO (validated) from request body
     * @return ResponseEntity containing the created Item and HTTP status 201 Created
     */
    @PostMapping
    public ResponseEntity<Item> create(@Valid @RequestBody ItemRequest req) {
        Item saved = service.save(toEntity(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Fetch an item by its identifier.
     *
     * @param id the positive identifier of the item
     * @return ResponseEntity containing the found Item and HTTP status 200 OK
     * @throws ResponseStatusException with status 404 if item not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Item> getById(
            @PathVariable @Positive Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found"));
    }

    /**
     * Update an existing item by its identifier.
     *
     * @param id  the positive identifier of the item to update
     * @param req the ItemRequest DTO (validated) containing updated data
     * @return ResponseEntity containing the updated Item and HTTP status 200 OK
     * @throws ResponseStatusException with status 404 if item not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Item> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ItemRequest req) {
        Item updated = service.findById(id)
                .map(existing -> {
                    existing.setName(req.getName());
                    existing.setDescription(req.getDescription());
                    existing.setStatus(req.getStatus());
                    existing.setEmail(req.getEmail());
                    return service.save(existing);
                })
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found"));
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete an item by its identifier.
     *
     * @param id the positive identifier of the item to delete
     * @return ResponseEntity with HTTP status 204 No Content if deletion succeeds
     * @throws ResponseStatusException with status 404 if item not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Long id) {
        if (!service.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Item not found");
        }
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Asynchronously process all items and return those processed.
     *
     * @return CompletableFuture wrapping a ResponseEntity with list of processed Items
     */
    @GetMapping("/process")
    public CompletableFuture<ResponseEntity<List<Item>>> processItems() {
        return service.processItemsAsync()
                .thenApply(ResponseEntity::ok);
    }
}
