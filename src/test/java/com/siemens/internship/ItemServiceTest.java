package com.siemens.internship;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import com.siemens.internship.service.ItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ItemService}.
 *
 * <p>Verifies delegation to the repository, exception propagation,
 * and asynchronous processing logic.</p>
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository repo;   // Mocked repository dependency

    @InjectMocks
    private ItemService service;   // Service under test, with mocks injected

    /**
     * Ensures findAll() returns exactly what the repository provides.
     */
    @Test
    void findAllDelegatesToRepository() {
        // given: a sample list of items from repo
        List<Item> items = List.of(
                new Item(1L, "n1", "d1", "NEW", "a@b.com"),
                new Item(2L, "n2", "d2", "NEW", "c@d.com")
        );
        when(repo.findAll()).thenReturn(items);

        // when: service calls findAll()
        List<Item> result = service.findAll();

        // then: result should be identical to repo output
        assertSame(items, result);
        verify(repo).findAll();
    }

    /**
     * Verifies existsById() delegates to the repository.
     */
    @Test
    void existsByIdDelegatesToRepository() {
        // given: repo reports id exists
        when(repo.existsById(5L)).thenReturn(true);

        // when / then: service should return true and call repo
        assertTrue(service.existsById(5L));
        verify(repo).existsById(5L);
    }

    /**
     * Verifies findById() delegates to the repository and returns Optional.
     */
    @Test
    void findByIdDelegatesToRepository() {
        // given: repo finds an item
        Item item = new Item(10L, "x", "y", "NEW", "e@f.com");
        when(repo.findById(10L)).thenReturn(Optional.of(item));

        // when: service calls findById()
        Optional<Item> result = service.findById(10L);

        // then: result should contain the same item and repo invoked
        assertTrue(result.isPresent());
        assertSame(item, result.get());
        verify(repo).findById(10L);
    }

    /**
     * Ensures save() returns the saved entity from the repository.
     */
    @Test
    void saveReturnsSavedEntity() {
        // given: an item without id and a saved version with id
        Item toSave = new Item(null, "n", "d", "NEW", "g@h.com");
        Item saved = new Item(1L, "n", "d", "NEW", "g@h.com");
        when(repo.save(toSave)).thenReturn(saved);

        // when: service.save() is called
        Item result = service.save(toSave);

        // then: result should be the saved entity
        assertSame(saved, result);
        verify(repo).save(toSave);
    }

    /**
     * Checks that save() propagates DataIntegrityViolationException.
     */
    @Test
    void savePropagatesDataIntegrityViolation() {
        // given: repo.save throws on duplicate
        Item bad = new Item(null, "n", "d", "NEW", "dup@i.com");
        when(repo.save(bad)).thenThrow(new DataIntegrityViolationException("dup"));

        // when / then: service.save should throw same exception
        assertThrows(DataIntegrityViolationException.class, () -> service.save(bad));
        verify(repo).save(bad);
    }

    /**
     * Verifies deleteById() invokes the repository delete.
     */
    @Test
    void deleteByIdInvokesRepository() {
        // given: no exception on delete
        doNothing().when(repo).deleteById(7L);

        // when: service.deleteById() is called
        service.deleteById(7L);

        // then: repo.deleteById should have been invoked
        verify(repo).deleteById(7L);
    }

    /**
     * Tests that processAndSave() updates status and saves via repo asynchronously.
     */
    @Test
    void processAndSaveCompletesWithProcessedItem() throws Exception {
        // given: an item to process
        Item item = new Item(2L, "n", "d", "NEW", "j@k.com");
        // stub repo.save to return the same item
        when(repo.save(item)).thenAnswer(inv -> inv.getArgument(0));

        // when: service.processAndSave is invoked
        CompletableFuture<Item> future = service.processAndSave(item);

        // then: future completes with status "PROCESSED" and repo.save called
        Item result = future.get(1, TimeUnit.SECONDS);
        assertEquals("PROCESSED", result.getStatus(),
                "status should be updated to PROCESSED");
        verify(repo).save(item);
    }

    /**
     * Ensures processAndSave() returns an exceptional future on save error.
     */
    @Test
    void processAndSaveReturnsExceptionalFutureOnError() {
        // given: repo.save throws runtime exception
        Item item = new Item(3L, "n", "d", "NEW", "l@m.com");
        when(repo.save(item)).thenThrow(new RuntimeException("fail"));

        // when: service.processAndSave is called
        CompletableFuture<Item> future = service.processAndSave(item);

        // then: future is completed exceptionally and get() throws
        assertTrue(future.isCompletedExceptionally());
        assertThrows(Exception.class, future::get);
        verify(repo).save(item);
    }

    /**
     * Tests that processItemsAsync() returns only successfully processed items.
     */
    @Test
    void processItemsAsyncReturnsOnlySuccessfulResults() throws Exception {
        // given: two items, repo.findAll() returns both
        Item good = new Item(1L, "a", "d", "NEW", "x@y.com");
        Item bad = new Item(2L, "b", "d", "NEW", "z@w.com");
        when(repo.findAll()).thenReturn(List.of(good, bad));

        // stub repo.save to succeed once then fail
        doAnswer(new Answer<Item>() {
            private int count = 0;
            @Override
            public Item answer(InvocationOnMock inv) {
                Item arg = inv.getArgument(0);
                if (++count == 1) {
                    return arg;  // first save succeeds
                } else {
                    throw new DataIntegrityViolationException("conflict"); // second save fails
                }
            }
        }).when(repo).save(any(Item.class));

        // when: service.processItemsAsync is called
        CompletableFuture<List<Item>> future = service.processItemsAsync();
        List<Item> result = future.get(2, TimeUnit.SECONDS);

        // then: only the successfully processed item should be in the result
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        // verify both save attempts occurred
        verify(repo, times(2)).save(any(Item.class));
    }
}
