package com.coffeeshop.operational;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity representing any domain entity in the system using a generic schema.
 * <p>
 * The metadata-driven architecture stores all operational data in this single table.
 * The 'type' field identifies the entity type (e.g., "Employee", "Transaction"),
 * and the 'payload' JSON field contains all attributes specific to that type.
 * <p>
 * Validation against the MetaType schema is performed by ValidationEngineService
 * before persistence.
 */
@Entity
@Table(name = "entity_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}

