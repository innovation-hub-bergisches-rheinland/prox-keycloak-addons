package de.innovationhub.prox.keycloak.kafka.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.innovationhub.prox.keycloak.kafka.KafkaConfigurationProperties;
import de.innovationhub.prox.keycloak.kafka.KafkaProducerFactory;
import de.innovationhub.prox.keycloak.kafka.ProducerNotAvailableException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

public class KafkaPublishingEventListenerProvider implements EventListenerProvider {

  private static final Logger LOGGER = Logger.getLogger(KafkaPublishingEventListenerProvider.class);
  private final String topicEvents;
  private final String topicAdminEvents;
  private final ObjectMapper objectMapper;
  private final KafkaProducerFactory producerFactory;

  public KafkaPublishingEventListenerProvider(
    String topicEvents,
    String topicAdminEvents,
    KafkaProducerFactory factory) {
    this.topicEvents = topicEvents;
    this.topicAdminEvents = topicAdminEvents;
    this.objectMapper = new ObjectMapper();
    this.producerFactory = factory;
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
    } catch (ProducerNotAvailableException e) {
      LOGGER.error(e.getMessage(), e);
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
    } catch (ProducerNotAvailableException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  @Override
  public void close() {

  }

  /**
   *
   * @param event
   * @param topic
   * @throws de.innovationhub.prox.keycloak.kafka.ProducerNotAvailableException
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   */
  private void produceEvent(String event, String topic)
    throws InterruptedException, ExecutionException, TimeoutException {
    var producer = this.producerFactory.getProducer();

    try {
      LOGGER.debugf("Producing event to topic '%s'", topic);
      ProducerRecord<String, String> record = new ProducerRecord<>(topic, event);

      Future<RecordMetadata> metaData = producer.send(record);

      RecordMetadata recordMetadata = metaData.get(30, TimeUnit.SECONDS);
      LOGGER.debugf("Produced event to topic '%s'", recordMetadata.topic());
    } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
      // We can't recover from these exceptions, so our only option is to close the producer and exit.
      LOGGER.error("Error producing event to Kafka", e);
      this.producerFactory.closeProducer();
    } catch (KafkaException e) {
      // For all other exceptions, just abort the transaction and try again.
      LOGGER.error("Error producing event to Kafka", e);
    }
  }
}
