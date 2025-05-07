package com.siemens.internship.repository;

import com.siemens.internship.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link Item} persistence operations.
 *
 * Provides CRUD functionality inherited from JpaRepository,
 * plus custom queries for optimized async processing.
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Retrieve all Item IDs without loading full entity data.
     *
     * Useful for bulk or asynchronous processing to avoid unnecessary data fetch.
     *
     * @return a List of all Item primary key values (IDs)
     */
    @Query("SELECT i.id FROM Item i")
    List<Long> findAllIds();
}
