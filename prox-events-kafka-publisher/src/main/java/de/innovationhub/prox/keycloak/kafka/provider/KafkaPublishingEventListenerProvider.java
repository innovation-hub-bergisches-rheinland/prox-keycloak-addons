package de.innovationhub.prox.keycloak.kafka.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.Message;
import de.innovationhub.prox.keycloak.kafka.KafkaProducerFactory;
import de.innovationhub.prox.keycloak.kafka.data.EventData;
import de.innovationhub.prox.keycloak.kafka.data.OperationType;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

public class KafkaPublishingEventListenerProvider implements EventListenerProvider {

  private static final Logger LOGGER = Logger.getLogger(KafkaPublishingEventListenerProvider.class);
  private final String topicEvents;
  private final String topicAdminEvents;
  private final Producer<String, Message> producer;

  public KafkaPublishingEventListenerProvider(String bootstrapServers, String clientId, String topicEvents,
    String topicAdminEvents, Map<String, Object> kafkaProducerProperties, KafkaProducerFactory factory) {
    this.topicEvents = topicEvents;
    this.topicAdminEvents = topicAdminEvents;

    producer = factory.createProducer(clientId, bootstrapServers, kafkaProducerProperties);
  }

  @Override
  public void onEvent(Event event) {
    var eventProtoBuilder = de.innovationhub.prox.keycloak.kafka.data.Event.newBuilder();
    // Must never be null
    eventProtoBuilder.setType(event.getType().name());

    if(event.getRealmId() != null) {
      eventProtoBuilder.setRealmId(event.getRealmId());
    }
    if(event.getIpAddress() != null) {
      eventProtoBuilder.setIpAddress(event.getIpAddress());
    }
    if(event.getClientId() != null) {
      eventProtoBuilder.setClientId(event.getClientId());
    }
    if(event.getSessionId() != null) {
      eventProtoBuilder.setSessionId(event.getSessionId());
    }
    if(event.getUserId() != null) {
      eventProtoBuilder.setUserId(event.getUserId());
    }

    if(event.getError() != null) {
      eventProtoBuilder.setError(event.getError());
    }

    if(event.getDetails() != null) {
      event.getDetails().forEach(eventProtoBuilder::putDetails);
    }

    try {
      produceEvent(eventProtoBuilder.build(), topicEvents);
    } catch (ExecutionException | TimeoutException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {
    var eventProtoBuilder = de.innovationhub.prox.keycloak.kafka.data.AdminEvent.newBuilder();
    // Must never be null
    eventProtoBuilder.setOperationType(OperationType.valueOf(event.getOperationType().name()));
    eventProtoBuilder.setTime(event.getTime());

    // I think the auth details should also never be null. However, Keycloak documentation sucks
    // and I don't know that for sure.
    var authDetails = event.getAuthDetails();
    if(authDetails != null) {
      var authDetailsBuilder = eventProtoBuilder.getAuthDetailsBuilder();
      if(authDetails.getClientId() != null) {
        authDetailsBuilder.setClientId(authDetailsBuilder.getClientId());
      }
      if(authDetails.getIpAddress() != null) {
        authDetailsBuilder.setIpAddress(authDetailsBuilder.getIpAddress());
      }
      if(authDetails.getRealmId() != null) {
        authDetailsBuilder.setRealmId(authDetailsBuilder.getRealmId());
      }
      if(authDetails.getUserId() != null) {
        authDetailsBuilder.setUserId(authDetailsBuilder.getUserId());
      }
    }

    if(includeRepresentation && event.getRepresentation() != null) {
      eventProtoBuilder.setRepresentation(event.getRepresentation());
    }
    if(event.getError() != null) {
      eventProtoBuilder.setError(event.getError());
    }

    try {
      produceEvent(eventProtoBuilder.build(), topicAdminEvents);
    } catch (ExecutionException | TimeoutException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {

  }

  private void produceEvent(Message event, String topic)
    throws InterruptedException, ExecutionException, TimeoutException {
    LOGGER.debugf("Producing event to topic '%s'", topic);
    ProducerRecord<String, Message> record = new ProducerRecord<>(topic, event);
    Future<RecordMetadata> metaData = producer.send(record);
    RecordMetadata recordMetadata = metaData.get(30, TimeUnit.SECONDS);
    LOGGER.debugf("Produced event to topic '%s'", recordMetadata.topic());
  }
}
