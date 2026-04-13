package com.coffeeshop.operational;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "entity_data", indexes = {@Index(name = "idx_entity_data_type", columnList = "type"), @Index(name = "idx_entity_data_created_at", columnList = "created_at DESC"), @Index(name = "idx_entity_data_updated_at", columnList = "updated_at DESC")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityData {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String type;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private com.fasterxml.jackson.databind.JsonNode payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}

