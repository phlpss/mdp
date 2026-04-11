package com.coffeeshop.operational;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for EntityData entities.
 * Provides database access for the metadata-driven entity store.
 */
@Repository
public interface EntityDataRepository extends JpaRepository<EntityData, UUID> {
    /**
     * Find all entities of a specific type with pagination support.
     */
    Page<EntityData> findByType(String type, Pageable pageable);

    /**
     * Find a single entity by type and UUID.
     */
    EntityData findByTypeAndId(String type, UUID id);
}

