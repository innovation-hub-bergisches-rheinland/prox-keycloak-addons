package de.innovationhub.prox.keycloak.redis.provider;

import de.innovationhub.prox.keycloak.redis.RedisConfigurationProperties;
import de.innovationhub.prox.keycloak.redis.RedisClientFactory;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class RedisPublishingEventListenerProviderFactory implements EventListenerProviderFactory {

  private static final Logger LOGGER  = Logger.getLogger(
    RedisPublishingEventListenerProviderFactory.class);

  private RedisConfigurationProperties redisConfiguration;

  private RedisPublishingEventListenerProvider instance;

  @Override
  public EventListenerProvider create(KeycloakSession keycloakSession) {
    if (instance == null) {
      instance = new RedisPublishingEventListenerProvider(
        this.redisConfiguration,
        new RedisClientFactory(this.redisConfiguration)
      );
    }

    return instance;
  }

  @Override
  public void init(Scope scope) {
    this.redisConfiguration = new RedisConfigurationProperties(
      scope.get("redisHost", System.getenv("REDIS_HOST")),
      scope.getInt("redisPort", Integer.valueOf(System.getenv("REDIS_PORT"))),
      scope.get("redisKeycloakEventChannel", System.getenv("REDIS_KEYCLOAK_EVENT_CHANNEL")),
      scope.get("redisKeycloakAdminEventChannel", System.getenv("REDIS_KEYCLOAK_ADMIN_EVENT_CHANNEL"))
    );

    LOGGER.info("RedisPublishingEventListenerProviderFactory initialized with configuration: " +
      this.redisConfiguration);
  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

  }

  @Override
  public void close() {
  }

  @Override
  public String getId() {
    return "prox-events-redis-publisher";
  }
}
