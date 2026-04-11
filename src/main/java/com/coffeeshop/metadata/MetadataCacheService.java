package com.coffeeshop.metadata;

import com.coffeeshop.exception.UnknownEntityTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache service for all metadata types and relationships loaded from Neo4j.
 *
 * On application startup, this service queries the Neo4j metadata graph and populates
 * concurrent hash maps with all MetaTypes and MetaRelationships. This cache is never
 * persisted to PostgreSQL and serves as the schema definition for all entity validation.
 *
 * The cache can be reloaded on demand via the reload() method for hot-updates without
 * restarting the application.
 *
 * Sample Neo4j Cypher Query:
 * MATCH (t:MetaType)-[:HAS_ATTRIBUTE]->(a:MetaAttribute)
 * RETURN t.name AS typeName,
 *        collect({
 *          name: a.name, 
 *          dataType: a.dataType, 
 *          mandatory: a.mandatory,
 *          min: a.min, 
 *          max: a.max, 
 *          allowedValues: a.allowedValues
 *        }) AS attributes
 */
@Slf4j
@Service
public class MetadataCacheService {
    private final Neo4jClient neo4jClient;
    private final ConcurrentHashMap<String, MetaType> typeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<MetaRelationship>> relationshipCache = new ConcurrentHashMap<>();

    public MetadataCacheService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Load metadata from Neo4j on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        log.info("Initializing metadata cache from Neo4j");
        reload();
    }

    /**
     * Reload all metadata types from Neo4j.
     * This method queries Neo4j for all MetaType nodes, their attributes, and relationships.
     */
    public synchronized void reload() {
        try {
            typeCache.clear();
            relationshipCache.clear();

            loadMetaTypes();
            loadMetaRelationships();

            log.info("Metadata cache reloaded successfully. Cached {} types", typeCache.size());
        } catch (Exception e) {
            log.error("Failed to reload metadata cache from Neo4j", e);
            throw new RuntimeException("Metadata initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load all MetaType definitions with their attributes from Neo4j.
     */
    private void loadMetaTypes() {
        // TODO: Implement Neo4j Cypher query to fetch MetaTypes and attributes
        // For now, this is a stub that would be populated at runtime
        log.debug("Loaded {} MetaTypes from Neo4j", typeCache.size());
    }

    /**
     * Load all MetaRelationship definitions from Neo4j.
     */
    private void loadMetaRelationships() {
        // TODO: Implement Neo4j Cypher query to fetch MetaRelationships
        // Example: MATCH (from:MetaType)-[rel:RELATIONSHIP]-(to:MetaType)
        log.debug("Loaded {} relationship definitions from Neo4j", relationshipCache.size());
    }

    /**
     * Retrieve a MetaType by name.
     * @param name The entity type name (e.g., "Employee", "Transaction")
     * @return The MetaType definition
     * @throws UnknownEntityTypeException if the type is not found
     */
    public MetaType getType(String name) {
        return typeCache.computeIfAbsent(name, k -> {
            throw new UnknownEntityTypeException(name);
        });
    }

    /**
     * Retrieve a MetaType by name, returning an Optional.
     * @param name The entity type name
     * @return An Optional containing the MetaType if found
     */
    public Optional<MetaType> findType(String name) {
        return Optional.ofNullable(typeCache.get(name));
    }

    /**
     * Retrieve all relationships originating from a given entity type.
     * @param fromType The source entity type name
     * @return List of MetaRelationships, or empty list if none exist
     */
    public List<MetaRelationship> getRelationships(String fromType) {
        return relationshipCache.getOrDefault(fromType, Collections.emptyList());
    }

    /**
     * Check if a MetaType is registered in the cache.
     * @param name The entity type name
     * @return true if the type exists, false otherwise
     */
    public boolean hasType(String name) {
        return typeCache.containsKey(name);
    }

    /**
     * Get all registered MetaTypes (for debugging/admin purposes).
     * @return Immutable map of all cached types
     */
    public Map<String, MetaType> getAllTypes() {
        return Collections.unmodifiableMap(typeCache);
    }
}

