package de.innovationhub.prox.keycloak.kafka.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.innovationhub.prox.keycloak.kafka.KafkaProducerFactory;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

public class KafkaPublishingEventListenerProvider implements EventListenerProvider {

  private static final Logger LOGGER = Logger.getLogger(KafkaPublishingEventListenerProvider.class);
  private final String topicEvents;
  private final String topicAdminEvents;
  private final Producer<String, String> producer;
  private final ObjectMapper objectMapper;

  public KafkaPublishingEventListenerProvider(String bootstrapServers, String clientId, String topicEvents,
    String topicAdminEvents, Map<String, Object> kafkaProducerProperties, KafkaProducerFactory factory) {
    this.topicEvents = topicEvents;
    this.topicAdminEvents = topicAdminEvents;
    this.objectMapper = new ObjectMapper();

    producer = factory.createProducer(clientId, bootstrapServers, kafkaProducerProperties);
  }

  @Override
  public void onEvent(Event event) {
    try {
      produceEvent(objectMapper.writeValueAsString(event), topicEvents);
    } catch (ExecutionException | TimeoutException | JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {
    try {
      produceEvent(objectMapper.writeValueAsString(event), topicAdminEvents);
    } catch (ExecutionException | TimeoutException | JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {

  }

  private void produceEvent(String event, String topic)
    throws InterruptedException, ExecutionException, TimeoutException {
    LOGGER.debugf("Producing event to topic '%s'", topic);
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, event);
    Future<RecordMetadata> metaData = producer.send(record);
    RecordMetadata recordMetadata = metaData.get(30, TimeUnit.SECONDS);
    LOGGER.debugf("Produced event to topic '%s'", recordMetadata.topic());
  }
}
