package de.innovationhub.prox.keycloak.redis;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.http.entity.ContentType;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.MountableFile;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

@Testcontainers
class RedisPublisherIntegrationTest {
  static final String TEST_REALM = "innovation-hub-bergisches-rheinland";

  static final KeycloakContainer KEYCLOAK_CONTAINER;
  static final GenericContainer<?> REDIS_CONTAINER;

  static final String REDIS_KEYCLOAK_EVENT_CHANNEL = "event.keycloak.events";
  static final String REDIS_KEYCLOAK_ADMIN_EVENT_CHANNEL = "event.keycloak.adminevents";

  static final JedisPool REDIS_CLIENT;
  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    Network network = Network.newNetwork();
    REDIS_CONTAINER = new GenericContainer<>("redis:7.0")
      .withNetwork(network)
      .withExposedPorts(6379)
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("redis.conf"),
        "/usr/local/etc/redis/redis.conf")
      .withCommand("redis-server /usr/local/etc/redis/redis.conf")
      .withNetworkAliases("redis");
    REDIS_CONTAINER.start();
    REDIS_CLIENT = new JedisPool("localhost", REDIS_CONTAINER.getMappedPort(6379));

    List<File> dependencies = Maven.resolver()
      .loadPomFromFile("./pom.xml")
      .resolve("redis.clients:jedis")
      .withTransitivity().asList(File.class);
    KEYCLOAK_CONTAINER = new KeycloakContainer()
      .withLogConsumer(of -> System.out.println(of.getUtf8String()))
      .withNetwork(network)
      .withNetworkAliases("keycloak")
      .withEnv(
        Map.of(
          "REDIS_HOST", "redis",
          "REDIS_PORT", "6379",
          "REDIS_KEYCLOAK_EVENT_CHANNEL", REDIS_KEYCLOAK_EVENT_CHANNEL,
          "REDIS_KEYCLOAK_ADMIN_EVENT_CHANNEL", REDIS_KEYCLOAK_ADMIN_EVENT_CHANNEL
        ))
      .withProviderClassesFrom("target/classes")
      .withProviderLibsFrom(dependencies)
      .withRealmImportFile("test-realm.json");
    KEYCLOAK_CONTAINER.start();
  }

  @BeforeAll
  static void setup() {
    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    var eventConfig = realm.getRealmEventsConfig();
    eventConfig.setEventsListeners(List.of("prox-events-redis-publisher"));
    realm.updateRealmEventsConfig(eventConfig);
  }

  @Test
  void shouldPublishAdminEvent() throws IOException, InterruptedException {
    var queue = new LinkedBlockingQueue<String>();
    var pubSub = new JedisPubSub() {
      @Override
      public void onMessage(String channel, String message) {
        AdminEvent adminEvent = null;
        try {
          adminEvent = OBJECT_MAPPER.readValue(message, AdminEvent.class);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        if(adminEvent.getOperationType() == OperationType.CREATE) {
          try {
            queue.put(message);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }
    };

    try (var jedis = REDIS_CLIENT.getResource()) {
      jedis.psubscribe(pubSub, REDIS_KEYCLOAK_ADMIN_EVENT_CHANNEL);
    }

    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    // Publish event
    var groupRepresentation = new GroupRepresentation();
    groupRepresentation.setName("test-group");
    realm.groups().add(groupRepresentation);

    var event = queue.poll(5, TimeUnit.SECONDS);
    var adminEvent = OBJECT_MAPPER.readValue(event, AdminEvent.class);

    assertThat(adminEvent).isNotNull();
  }

  @Test
  void shouldPublishEvent() throws Exception {

    var queue = new LinkedBlockingQueue<String>();
    var pubSub = new JedisPubSub() {
      @Override
      public void onMessage(String channel, String message) {
        Event event = null;
        try {
          event = OBJECT_MAPPER.readValue(message, Event.class);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        if(event.getType() == EventType.CLIENT_LOGIN) {
          try {
            queue.put(message);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }
    };

    try (var jedis = REDIS_CLIENT.getResource()) {
      jedis.psubscribe(pubSub, REDIS_KEYCLOAK_ADMIN_EVENT_CHANNEL);
    }

    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    // Create a new client
    var client = new ClientRepresentation();
    client.setName("test-client");
    client.setSecret("secret");
    client.setClientId("test-client");
    client.setServiceAccountsEnabled(true);
    realm.clients().create(client);

    var tokenUrl = "http://" + KEYCLOAK_CONTAINER.getHost() + ":" + KEYCLOAK_CONTAINER.getHttpPort() + "/realms/" + TEST_REALM + "/protocol/openid-connect/token";

    given()
      .contentType(ContentType.APPLICATION_FORM_URLENCODED.getMimeType())
      .formParam("client_id", client.getClientId())
      .formParam("client_secret", client.getSecret())
      .formParam("grant_type", "client_credentials")
      .when()
      .post(tokenUrl)
      .then()
      .log().ifValidationFails()
      .statusCode(200);

    var event = queue.poll(5, TimeUnit.SECONDS);
    var clientEvent = OBJECT_MAPPER.readValue(event, Event.class);

    assertThat(clientEvent).isNotNull();
  }

}
