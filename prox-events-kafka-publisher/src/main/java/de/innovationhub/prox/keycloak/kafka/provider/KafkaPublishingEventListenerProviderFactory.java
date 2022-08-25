package de.innovationhub.prox.keycloak.kafka.provider;

import de.innovationhub.prox.keycloak.kafka.KafkaProducerFactory;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class KafkaPublishingEventListenerProviderFactory implements EventListenerProviderFactory {

  private static final Logger LOGGER  = Logger.getLogger(KafkaPublishingEventListenerProviderFactory.class);
  private String bootstrapServers;
  private String topicEvents;
  private String topicAdminEvents;
  private String clientId;
  private String schemaRegistryUrl;

  private KafkaPublishingEventListenerProvider instance;

  @Override
  public EventListenerProvider create(KeycloakSession keycloakSession) {
    if (instance == null) {
      instance = new KafkaPublishingEventListenerProvider(bootstrapServers,
        clientId,
        topicEvents,
        topicAdminEvents,
        Map.of(
          "schema.registry.url", schemaRegistryUrl
        ),
        new KafkaProducerFactory());
    }

    return instance;
  }

  @Override
  public void init(Scope scope) {
    topicEvents = scope.get("topicEvents", System.getenv("KAFKA_TOPIC"));
    clientId = scope.get("clientId", System.getenv("KAFKA_CLIENT_ID"));
    bootstrapServers = scope.get("bootstrapServers", System.getenv("KAFKA_BOOTSTRAP_SERVERS"));
    topicAdminEvents = scope.get("topicAdminEvents", System.getenv("KAFKA_ADMIN_TOPIC"));
    schemaRegistryUrl = scope.get("schemaRegistryUrl", System.getenv("SCHEMA_REGISTRY_URL"));
  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

  }

  @Override
  public void close() {
  }

  @Override
  public String getId() {
    return "prox-events-kafka-publisher";
  }
}
