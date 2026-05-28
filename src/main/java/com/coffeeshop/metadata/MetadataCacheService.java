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
                           label:         ma.label,
                           dataType:      ma.dataType,
                           mandatory:     ma.mandatory,
                           sensitive:     ma.sensitive,
                           sortable:      ma.sortable,
                           filterable:    ma.filterable,
                           showInList:    ma.showInList,
                           showInForm:    ma.showInForm,
                           readOnly:      ma.readOnly,
                           min:           ma.min,
                           max:           ma.max,
                           allowedValues: ma.allowedValues,
                           placeholder:   ma.placeholder,
                           hint:          ma.hint,
                           order:         ma.order,
                           group:         ma.group
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
                            (String) a.getOrDefault("label", a.get("name")),
                            (String) a.get("dataType"),
                            Boolean.TRUE.equals(a.get("mandatory")),
                            Boolean.TRUE.equals(a.get("sensitive")),
                            !Boolean.FALSE.equals(a.get("sortable")),
                            Boolean.TRUE.equals(a.get("filterable")),
                            Boolean.TRUE.equals(a.get("showInList")),
                            !Boolean.FALSE.equals(a.get("showInForm")),
                            Boolean.TRUE.equals(a.get("readOnly")),
                            toInteger(a.get("min")),
                            toInteger(a.get("max")),
                            toStringList(a.get("allowedValues")),
                            (String) a.get("placeholder"),
                            (String) a.get("hint"),
                            toInteger(a.get("order")) != null ? toInteger(a.get("order")) : 99,
                            (String) a.get("group")))
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

    /**
     * Create a new MetaType node in Neo4j and add it to the cache.
     */
    public synchronized MetaType createType(String name, List<String> sensitiveFields) {
        String cypher = """
                MERGE (mt:MetaType {name: $name})
                ON CREATE SET mt.sensitiveFields = $sensitiveFields
                RETURN mt.name AS typeName
                """;
        neo4jClient.query(cypher)
                .bind(name).to("name")
                .bind(sensitiveFields != null ? sensitiveFields : List.of()).to("sensitiveFields")
                .run();

        MetaType created = new MetaType(name, List.of(),
                sensitiveFields != null ? sensitiveFields : List.of());
        typeCache.put(name, created);
        log.info("Created MetaType '{}' in Neo4j and cache", name);
        return created;
    }

    /**
     * Update sensitiveFields on an existing MetaType in Neo4j and refresh cache.
     */
    public synchronized MetaType updateType(String name, List<String> sensitiveFields) {
        if (!typeCache.containsKey(name)) throw new UnknownEntityTypeException(name);

        String cypher = """
                MATCH (mt:MetaType {name: $name})
                SET mt.sensitiveFields = $sensitiveFields
                """;
        neo4jClient.query(cypher)
                .bind(name).to("name")
                .bind(sensitiveFields != null ? sensitiveFields : List.of()).to("sensitiveFields")
                .run();

        MetaType existing = typeCache.get(name);
        MetaType updated = new MetaType(name, existing.attributes(),
                sensitiveFields != null ? sensitiveFields : List.of());
        typeCache.put(name, updated);
        log.info("Updated MetaType '{}' sensitiveFields in Neo4j and cache", name);
        return updated;
    }

    /**
     * Add a new MetaAttribute node to a MetaType in Neo4j and update cache.
     */
    public synchronized MetaType addAttribute(String typeName, MetaAttribute attr) {
        MetaType existing = typeCache.get(typeName);
        if (existing == null) throw new UnknownEntityTypeException(typeName);

        boolean alreadyExists = existing.attributes().stream()
                .anyMatch(a -> a.name().equals(attr.name()));
        if (alreadyExists) throw new IllegalArgumentException(
                "Attribute '" + attr.name() + "' already exists on type '" + typeName + "'");

        String cypher = """
                MATCH (mt:MetaType {name: $typeName})
                CREATE (ma:MetaAttribute {
                    name:          $attrName,
                    label:         $label,
                    dataType:      $dataType,
                    mandatory:     $mandatory,
                    sensitive:     $sensitive,
                    sortable:      $sortable,
                    filterable:    $filterable,
                    showInList:    $showInList,
                    showInForm:    $showInForm,
                    readOnly:      $readOnly,
                    min:           $min,
                    max:           $max,
                    allowedValues: $allowedValues,
                    placeholder:   $placeholder,
                    hint:          $hint,
                    order:         $order,
                    group:         $group
                })
                CREATE (mt)-[:HAS_ATTRIBUTE]->(ma)
                """;
        neo4jClient.query(cypher)
                .bind(typeName).to("typeName")
                .bind(attr.name()).to("attrName")
                .bind(attr.label()).to("label")
                .bind(attr.dataType()).to("dataType")
                .bind(attr.mandatory()).to("mandatory")
                .bind(attr.sensitive()).to("sensitive")
                .bind(attr.sortable()).to("sortable")
                .bind(attr.filterable()).to("filterable")
                .bind(attr.showInList()).to("showInList")
                .bind(attr.showInForm()).to("showInForm")
                .bind(attr.readOnly()).to("readOnly")
                .bind(attr.min()).to("min")
                .bind(attr.max()).to("max")
                .bind(attr.allowedValues() != null ? attr.allowedValues() : List.of()).to("allowedValues")
                .bind(attr.placeholder()).to("placeholder")
                .bind(attr.hint()).to("hint")
                .bind(attr.order()).to("order")
                .bind(attr.group()).to("group")
                .run();

        List<MetaAttribute> updatedAttrs = new java.util.ArrayList<>(existing.attributes());
        updatedAttrs.add(attr);
        MetaType updated = new MetaType(typeName, List.copyOf(updatedAttrs), existing.sensitiveFields());
        typeCache.put(typeName, updated);
        log.info("Added attribute '{}' to MetaType '{}'", attr.name(), typeName);
        return updated;
    }

    /**
     * Update an existing MetaAttribute on a MetaType in Neo4j and refresh cache.
     */
    public synchronized MetaType updateAttribute(String typeName, String attrName, MetaAttribute newAttr) {
        MetaType existing = typeCache.get(typeName);
        if (existing == null) throw new UnknownEntityTypeException(typeName);

        boolean exists = existing.attributes().stream().anyMatch(a -> a.name().equals(attrName));
        if (!exists) throw new IllegalArgumentException(
                "Attribute '" + attrName + "' not found on type '" + typeName + "'");

        String cypher = """
                MATCH (mt:MetaType {name: $typeName})-[:HAS_ATTRIBUTE]->(ma:MetaAttribute {name: $attrName})
                SET ma.label         = $label,
                    ma.dataType      = $dataType,
                    ma.mandatory     = $mandatory,
                    ma.sensitive     = $sensitive,
                    ma.sortable      = $sortable,
                    ma.filterable    = $filterable,
                    ma.showInList    = $showInList,
                    ma.showInForm    = $showInForm,
                    ma.readOnly      = $readOnly,
                    ma.min           = $min,
                    ma.max           = $max,
                    ma.allowedValues = $allowedValues,
                    ma.placeholder   = $placeholder,
                    ma.hint          = $hint,
                    ma.order         = $order,
                    ma.group         = $group
                """;
        neo4jClient.query(cypher)
                .bind(typeName).to("typeName")
                .bind(attrName).to("attrName")
                .bind(newAttr.label()).to("label")
                .bind(newAttr.dataType()).to("dataType")
                .bind(newAttr.mandatory()).to("mandatory")
                .bind(newAttr.sensitive()).to("sensitive")
                .bind(newAttr.sortable()).to("sortable")
                .bind(newAttr.filterable()).to("filterable")
                .bind(newAttr.showInList()).to("showInList")
                .bind(newAttr.showInForm()).to("showInForm")
                .bind(newAttr.readOnly()).to("readOnly")
                .bind(newAttr.min()).to("min")
                .bind(newAttr.max()).to("max")
                .bind(newAttr.allowedValues() != null ? newAttr.allowedValues() : List.of()).to("allowedValues")
                .bind(newAttr.placeholder()).to("placeholder")
                .bind(newAttr.hint()).to("hint")
                .bind(newAttr.order()).to("order")
                .bind(newAttr.group()).to("group")
                .run();

        List<MetaAttribute> updatedAttrs = existing.attributes().stream()
                .map(a -> a.name().equals(attrName) ? newAttr : a)
                .toList();
        MetaType updated = new MetaType(typeName, updatedAttrs, existing.sensitiveFields());
        typeCache.put(typeName, updated);
        log.info("Updated attribute '{}' on MetaType '{}'", attrName, typeName);
        return updated;
    }

    /**
     * Delete a MetaAttribute from a MetaType in Neo4j and update cache.
     */
    public synchronized MetaType deleteAttribute(String typeName, String attrName) {
            MetaType existing = typeCache.get(typeName);
        if (existing == null) throw new UnknownEntityTypeException(typeName);

        boolean exists = existing.attributes().stream().anyMatch(a -> a.name().equals(attrName));
        if (!exists) throw new IllegalArgumentException(
                "Attribute '" + attrName + "' not found on type '" + typeName + "'");

        String cypher = """
                MATCH (mt:MetaType {name: $typeName})-[r:HAS_ATTRIBUTE]->(ma:MetaAttribute {name: $attrName})
                DELETE r, ma
                """;
        neo4jClient.query(cypher)
                .bind(typeName).to("typeName")
                .bind(attrName).to("attrName")
                .run();

        List<MetaAttribute> updatedAttrs = existing.attributes().stream()
                .filter(a -> !a.name().equals(attrName))
                .toList();
        MetaType updated = new MetaType(typeName, updatedAttrs, existing.sensitiveFields());
        typeCache.put(typeName, updated);
        log.info("Deleted attribute '{}' from MetaType '{}'", attrName, typeName);
        return updated;
    }
}
