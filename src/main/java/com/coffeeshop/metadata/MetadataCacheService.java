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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory cache service for all metadata types and relationships loaded from Neo4j.
 * <p>
 * On application startup, this service queries the Neo4j metadata graph and populates
 * concurrent hash maps with all MetaTypes and MetaRelationships. This cache is never
 * persisted to PostgreSQL and serves as the schema definition for all entity validation.
 * <p>
 * The cache can be reloaded on demand via the reload() method for hot-updates without
 * restarting the application.
 * <p>
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
     *
     * Expected graph structure:
     *   (:MetaType {name, sensitiveFields})-[:HAS_ATTRIBUTE]->(:MetaAttribute {name, dataType, mandatory, min, max, allowedValues})
     */
    private void loadMetaTypes() {
        String cypher = """
                MATCH (mt:MetaType)
                OPTIONAL MATCH (mt)-[:HAS_ATTRIBUTE]->(ma:MetaAttribute)
                RETURN mt.name AS typeName,
                       mt.sensitiveFields AS sensitiveFields,
                       collect({
                           name:          ma.name,
                           dataType:      ma.dataType,
                           mandatory:     ma.mandatory,
                           min:           ma.min,
                           max:           ma.max,
                           allowedValues: ma.allowedValues
                       }) AS attributes
                """;

        neo4jClient.query(cypher).fetch().all().forEach(row -> {
            var typeName = (String) row.get("typeName");

            @SuppressWarnings("unchecked")
            var sensitiveFields = (List<String>) row.getOrDefault("sensitiveFields", List.of());

            @SuppressWarnings("unchecked")
            var rawAttrs = (List<Map<String, Object>>) row.getOrDefault("attributes", List.of());

            List<MetaAttribute> attributes = rawAttrs.stream()
                    .filter(a -> a.get("name") != null)
                    .map(a -> new MetaAttribute(
                            (String) a.get("name"),
                            (String) a.get("dataType"),
                            Boolean.TRUE.equals(a.get("mandatory")),
                            toInteger(a.get("min")),
                            toInteger(a.get("max")),
                            toStringList(a.get("allowedValues"))))
                    .toList();

            typeCache.put(typeName, new MetaType(typeName, attributes, sensitiveFields));
        });

        log.debug("Loaded {} MetaTypes from Neo4j", typeCache.size());
    }

    private static Integer toInteger(Object value) {
        return switch (value) {
            case Integer i -> i;
            case Number n -> n.intValue();
            case null, default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return (List<String>) list;
        return List.of();
    }

    /**
     * Load all MetaRelationship definitions from Neo4j.
     *
     * Expected graph structure:
     *   (:MetaRelationship {name, mandatory})-[:FROM_TYPE]->(:MetaType)
     *   (:MetaRelationship {name, mandatory})-[:TO_TYPE]->(:MetaType)
     */
    private void loadMetaRelationships() {
        String cypher = """
                MATCH (from:MetaType)-[r:RELATIONSHIP_DEF]->(to:MetaType)
                RETURN r.name             AS relName,
                       from.name          AS fromType,
                       to.name            AS toType,
                       r.cardinality      AS cardinality,
                       r.affects_analytics AS affectsAnalytics
                """;

        neo4jClient.query(cypher).fetch().all().forEach(row -> {
            var relName = (String) row.get("relName");
            var fromType = (String) row.get("fromType");
            var toType = (String) row.get("toType");
            var cardinality = (String) row.getOrDefault("cardinality", "");

            var mandatory = cardinality.startsWith("ONE_TO_ONE") || cardinality.startsWith("ONE_TO_MANY");
            var rel = new MetaRelationship(relName, fromType, toType, mandatory);

            relationshipCache.computeIfAbsent(fromType, k -> new CopyOnWriteArrayList<>()).add(rel);
        });

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
