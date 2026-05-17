CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

/**
 * ENTITY_DATA: Generic table for storing all domain entities.
 *
 * The metadata-driven architecture stores all entity data in a single JSONB column,
 * with the entity type (e.g. "Employee", "Transaction") stored separately for indexing
 * and efficient querying.
 *
 * Constraints:
 * - All payloads must conform to their MetaType schema defined in Neo4j
 * - Timestamps are managed automatically by the database
 * - UUID is generated server-side for deterministic ordering
 */
CREATE TABLE entity_data (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type       VARCHAR(100) NOT NULL,
    payload    JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_type_not_empty CHECK (type <> '')
);

-- Composite index for efficient type-based queries
CREATE INDEX idx_entity_data_type ON entity_data (type);

-- GIN index for JSONB queries (e.g. filtering by nested field values)
CREATE INDEX idx_entity_data_payload ON entity_data USING gin (payload);

-- Index for temporal queries
CREATE INDEX idx_entity_data_created_at ON entity_data (created_at DESC);

-- Performance optimization for updates
CREATE INDEX idx_entity_data_updated_at ON entity_data (updated_at DESC);

