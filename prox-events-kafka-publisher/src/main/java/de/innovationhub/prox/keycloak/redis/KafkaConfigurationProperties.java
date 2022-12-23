package de.innovationhub.prox.keycloak.redis;

import java.util.Map;

public class KafkaConfigurationProperties {
  private final String bootstrapServers;
  private final String clientId;
  private final Map<String, Object> kafkaProducerProperties;

  public KafkaConfigurationProperties(String bootstrapServers, String clientId,
    Map<String, Object> kafkaProducerProperties) {
    this.bootstrapServers = bootstrapServers;
    this.clientId = clientId;
    this.kafkaProducerProperties = kafkaProducerProperties;
  }

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public String getClientId() {
    return clientId;
  }

  public Map<String, Object> getKafkaProducerProperties() {
    return kafkaProducerProperties;
  }
}
