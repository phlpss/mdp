package com.coffeeshop.analytics;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for publishing entity events to Google Cloud Pub/Sub asynchronously.
 *
 * Implements the Observer pattern to decouple core API operations from analytics processing.
 * This ensures that Pub/Sub publishing failures do NOT degrade the availability of core
 * business operations. Events are published with exponential backoff retry logic (max 3 attempts).
 *
 * For production use, Cloud Functions or Dataflow pipelines consume these events and
 * stream them to BigQuery for analysis and anomaly detection.
 */
@Slf4j
@Service
@Profile("prod")
public class PubSubPublisherService {
    private final String projectId;
    private final String topicId;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private Publisher publisher;

    public PubSubPublisherService(@Value("${coffeeshop.pubsub.project-id}") String projectId, @Value("${coffeeshop.pubsub.topic-id}") String topicId, ObjectMapper objectMapper) {
        this.projectId = projectId;
        this.topicId = topicId;
        this.objectMapper = objectMapper;
        initializePublisher();
    }

    /**
     * Initialize the Pub/Sub Publisher client.
     */
    private void initializePublisher() {
        try {
            TopicName topic = TopicName.of(projectId, topicId);
            this.publisher = Publisher.newBuilder(topic).build();
            log.info("Pub/Sub Publisher initialized for topic: {}/{}", projectId, topicId);
        } catch (IOException e) {
            log.warn("Failed to initialize Pub/Sub Publisher - events will be logged only. Error: {}", e.getMessage());
        }
    }

    /**
     * Publish an entity event to Pub/Sub asynchronously with retry logic.
     *
     * @param eventType The type of event (e.g., "CREATED", "UPDATED", "DELETED")
     * @param entityType The entity type (e.g., "Employee", "Transaction")
     * @param entityId The UUID of the entity
     * @param payload The entity payload
     */
    public void publishEntityEvent(String eventType, String entityType, UUID entityId, JsonNode payload) {
        if (publisher == null) {
            log.debug("Pub/Sub Publisher not available. Skipping publish for {} event on entity {}/{}", eventType, entityType, entityId);
            return;
        }

        executorService.execute(() -> {
            try {
                publishWithRetry(eventType, entityType, entityId, payload, 0);
            } catch (Exception e) {
                log.error("Failed to publish event after max retries for entity {}/{}", entityType, entityId, e);
            }
        });
    }

    /**
     * Internal method to publish with exponential backoff retry (max 3 attempts).
     */
    private void publishWithRetry(String eventType, String entityType, UUID entityId, JsonNode payload, int attemptNumber) throws Exception {
        if (attemptNumber > 2) { // Max 3 attempts
            log.error("Max retries exceeded for entity {}/{}", entityType, entityId);
            return;
        }

        try {
            // Build event message
            ObjectNode eventMessage = objectMapper.createObjectNode();
            eventMessage.put("eventId", UUID.randomUUID().toString());
            eventMessage.put("eventType", eventType);
            eventMessage.put("entityType", entityType);
            eventMessage.put("entityId", entityId.toString());
            eventMessage.put("timestamp", Instant.now().toString());
            eventMessage.set("payload", payload);

            String messageJson = objectMapper.writeValueAsString(eventMessage);
            ByteString data = ByteString.copyFromUtf8(messageJson);
            PubsubMessage message = PubsubMessage.newBuilder().setData(data).build();

            ApiFuture<String> future = publisher.publish(message);
            String messageId = future.get(5, TimeUnit.SECONDS);

            log.debug("Published {} event for entity {}/{} with message ID: {}", eventType, entityType, entityId, messageId);
        } catch (Exception e) {
            log.warn("Attempt {} failed for {} event on entity {}/{}. Retrying...", attemptNumber + 1, eventType, entityType, entityId);

            // Exponential backoff: 100ms, 200ms, 400ms
            long delayMs = 100L * (long) Math.pow(2, attemptNumber);
            Thread.sleep(delayMs);

            publishWithRetry(eventType, entityType, entityId, payload, attemptNumber + 1);
        }
    }

    /**
     * Shutdown the publisher on application shutdown.
     */
    public void shutdown() {
        if (publisher != null) {
            try {
                publisher.shutdown();
                publisher.awaitTermination(1, TimeUnit.MINUTES);
                log.info("Pub/Sub Publisher shut down gracefully");
            } catch (Exception e) {
                log.error("Error during Pub/Sub Publisher shutdown", e);
            }
        }
        executorService.shutdown();
    }
}

