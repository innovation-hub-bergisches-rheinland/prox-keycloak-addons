package de.innovationhub.prox.keycloak.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisClientFactory {

  private JedisPool pool;

  public RedisClientFactory(RedisConfigurationProperties configurationProperties) {
    this.pool = new JedisPool(configurationProperties.getRedisHost(), configurationProperties.getRedisPort());
  }

  public Jedis getResource() {
    return this.pool.getResource();
  }

}
