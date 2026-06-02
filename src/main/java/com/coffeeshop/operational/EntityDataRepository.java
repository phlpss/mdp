package com.coffeeshop.operational;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = """
    SELECT * FROM entity_data
    WHERE jsonb_extract_path_text(payload, :fieldName) = :fieldValue
    """,
            countQuery = """
            SELECT COUNT(*) FROM entity_data
            WHERE jsonb_extract_path_text(payload, :fieldName) = :fieldValue
            """,
            nativeQuery = true)
    Page<EntityData> findByTypeAndPayloadField(
            @Param("type") String type,
            @Param("fieldName") String fieldName,
            @Param("fieldValue") String fieldValue,
            Pageable pageable);

    @Query(value = "SELECT payload ->> :fieldName FROM entity_data WHERE id = :id", nativeQuery = true)
    UUID extractPayloadField(@Param("id") UUID id, @Param("fieldName") String fieldName);

    @Query(value = """
            SELECT * FROM entity_data
            WHERE type = :type
              AND payload ->> :fieldName1 = :fieldValue1
            """,
            countQuery = """
                    SELECT COUNT(*) FROM entity_data
                    WHERE type = :type
                      AND payload ->> :fieldName1 = :fieldValue1
                    """,
            nativeQuery = true)
    Page<EntityData> findByTypeAndOnePayloadField(
            @Param("type") String type,
            @Param("fieldName1") String fieldName1,
            @Param("fieldValue1") String fieldValue1,
            Pageable pageable);

    @Query(value = """
            SELECT * FROM entity_data
            WHERE type = :type
              AND payload ->> :fieldName1 = :fieldValue1
              AND payload ->> :fieldName2 = :fieldValue2
            """,
            countQuery = """
                    SELECT COUNT(*) FROM entity_data
                    WHERE type = :type
                      AND payload ->> :fieldName1 = :fieldValue1
                      AND payload ->> :fieldName2 = :fieldValue2
                    """,
            nativeQuery = true)
    Page<EntityData> findByTypeAndTwoPayloadFields(
            @Param("type") String type,
            @Param("fieldName1") String fieldName1,
            @Param("fieldValue1") String fieldValue1,
            @Param("fieldName2") String fieldName2,
            @Param("fieldValue2") String fieldValue2,
            Pageable pageable);

    /**
     * Filters by type, a single JSONB field equality, and a JSONB date field range.
     * Used for schedule queries: storeLocationId = X AND shiftDate BETWEEN start AND end.
     * PostgreSQL casts the text value to date for the range comparison.
     */
    @Query(value = """
        SELECT * FROM entity_data
        WHERE type = :type
          AND payload ->> :fieldName = :fieldValue
          AND (payload ->> :dateField)::date BETWEEN :startDate AND :endDate
        """,
            countQuery = """
        SELECT COUNT(*) FROM entity_data
        WHERE type = :type
          AND payload ->> :fieldName = :fieldValue
          AND (payload ->> :dateField)::date BETWEEN :startDate AND :endDate
        """,
            nativeQuery = true)
    Page<EntityData> findByTypeAndPayloadFieldInDateRange(
            @Param("type")       String type,
            @Param("fieldName")  String fieldName,
            @Param("fieldValue") String fieldValue,
            @Param("dateField")  String dateField,
            @Param("startDate")  LocalDate startDate,
            @Param("endDate")    LocalDate endDate,
            Pageable pageable);
}

