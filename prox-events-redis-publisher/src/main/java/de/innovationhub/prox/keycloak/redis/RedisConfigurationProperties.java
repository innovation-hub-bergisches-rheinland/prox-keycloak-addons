package de.innovationhub.prox.keycloak.redis;

public class RedisConfigurationProperties {
  private final String redisHost;
  private final int redisPort;
  private final String eventChannel;
  private final String adminEventChannel;

  public RedisConfigurationProperties(String redisHost, int redisPort, String eventChannel,
    String adminEventChannel) {
    this.redisHost = redisHost;
    this.redisPort = redisPort;
    this.eventChannel = eventChannel;
    this.adminEventChannel = adminEventChannel;
  }

  public String getRedisHost() {
    return redisHost;
  }

  public int getRedisPort() {
    return redisPort;
  }

  public String getEventChannel() {
    return eventChannel;
  }

  public String getAdminEventChannel() {
    return adminEventChannel;
  }

  @Override
  public String toString() {
    return "RedisConfigurationProperties{" +
      "redisHost='" + redisHost + '\'' +
      ", redisPort=" + redisPort +
      ", eventChannel='" + eventChannel + '\'' +
      ", adminEventChannel='" + adminEventChannel + '\'' +
      '}';
  }
}
