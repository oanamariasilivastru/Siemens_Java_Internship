package com.siemens.internship.service;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service layer handling Item business operations, including CRUD,
 * transaction management, and asynchronous processing.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository repo;

    /**
     * Constructor for dependency injection.
     * @param repo the repository to use for Item persistence
     */
    public ItemService(ItemRepository repo) {
        this.repo = repo;
    }

    /**
     * Retrieve all items.
     * @return a List of all Items
     */
    public List<Item> findAll() {
        return repo.findAll();
    }

    /**
     * Check if an item exists by its identifier.
     * @param id the Item ID to check
     * @return true if an Item with the given ID exists, false otherwise
     */
    public boolean existsById(Long id) {
        return repo.existsById(id);
    }

    /**
     * Find an Item by its identifier.
     * @param id the Item ID to find
     * @return an Optional containing the Item if found, or empty if not
     */
    public java.util.Optional<Item> findById(Long id) {
        return repo.findById(id);
    }

    /**
     * Save or update an Item within a new transaction.
     * @param item the Item to save
     * @return the saved Item instance
     * @throws DataIntegrityViolationException if a database constraint is violated
     */
    @Transactional
    public Item save(Item item) {
        try {
            return repo.save(item);
        } catch (DataIntegrityViolationException ex) {
            throw ex;
        }
    }

    /**
     * Delete an Item by its identifier within a new transaction.
     * @param id the Item ID to delete
     */
    @Transactional
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    /**
     * Process and save a single Item asynchronously in a separate transaction.
     * @param item the Item to process
     * @return a CompletableFuture completing with the processed Item,
     *         or exceptionally if processing fails
     */
    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Item> processAndSave(Item item) {
        try {
            item.setStatus("PROCESSED");
            return CompletableFuture.completedFuture(repo.save(item));
        } catch (Exception ex) {
            CompletableFuture<Item> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    /**
     * Process all Items in parallel and collect only successful results.
     * Any failures are logged and excluded from the returned list.
     * @return a CompletableFuture containing a List of successfully processed Items
     */
    public CompletableFuture<List<Item>> processItemsAsync() {
        List<Item> allItems = repo.findAll();

        List<CompletableFuture<Item>> tasks = allItems.stream()
                .map(this::processAndSave)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .handle((ignored, aggregateEx) ->
                        tasks.stream()
                                .map(f -> f.handle((it, ex) -> {
                                    if (ex != null) {
                                        log.warn("Failed processing item id={}",
                                                it != null ? it.getId() : "?", ex);
                                        return null;
                                    }
                                    return it;
                                }))
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                );
    }
}
