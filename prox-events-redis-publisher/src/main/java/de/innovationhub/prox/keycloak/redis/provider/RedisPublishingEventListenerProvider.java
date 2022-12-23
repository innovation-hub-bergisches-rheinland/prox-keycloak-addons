package de.innovationhub.prox.keycloak.redis.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.innovationhub.prox.keycloak.redis.RedisClientFactory;
import de.innovationhub.prox.keycloak.redis.RedisConfigurationProperties;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import redis.clients.jedis.Jedis;

public class RedisPublishingEventListenerProvider implements EventListenerProvider {

  private static final Logger LOGGER = Logger.getLogger(RedisPublishingEventListenerProvider.class);
  private final RedisConfigurationProperties redisConfigurationProperties;
  private final RedisClientFactory redisClientFactory;
  private final ObjectMapper objectMapper;

  public RedisPublishingEventListenerProvider(
    RedisConfigurationProperties redisConfigurationProperties,
    RedisClientFactory factory) {
    this.redisConfigurationProperties = redisConfigurationProperties;
    this.redisClientFactory = factory;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void onEvent(Event event) {
    try {
      produceEvent(objectMapper.writeValueAsString(event), redisConfigurationProperties.getEventChannel());
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {
    try {
      produceEvent(objectMapper.writeValueAsString(event), redisConfigurationProperties.getAdminEventChannel());
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  @Override
  public void close() {

  }

  private void produceEvent(String event, String topic) {
    try (Jedis jedis = redisClientFactory.getResource()) {
      LOGGER.info("Publishing event to topic " + topic);
      jedis.publish(topic, event);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }
}
