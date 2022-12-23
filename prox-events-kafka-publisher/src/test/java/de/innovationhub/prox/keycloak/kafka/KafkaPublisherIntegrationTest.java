package de.innovationhub.prox.keycloak.kafka;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.InspectContainerResponse;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.http.entity.ContentType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.ComparableVersion;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class KafkaPublisherIntegrationTest {
  static final String TEST_REALM = "innovation-hub-bergisches-rheinland";

  static final KeycloakContainer KEYCLOAK_CONTAINER;
  static final RedpandaContainer REDPANDA_CONTAINER;

  static final String KAFKA_TOPIC = "event.keycloak.events";
  static final String KAFKA_ADMIN_TOPIC = "event.keycloak.adminevents";

  static {
    Network network = Network.newNetwork();
    REDPANDA_CONTAINER = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda:v22.2.1")
      .withNetwork(network)
      .withNetworkAliases("redpanda");
    REDPANDA_CONTAINER.start();

    List<File> dependencies = Maven.resolver()
      .loadPomFromFile("./pom.xml")
      .resolve("org.apache.kafka:kafka-clients")
      .withTransitivity().asList(File.class);
    KEYCLOAK_CONTAINER = new KeycloakContainer()
      .withLogConsumer(of -> System.out.println(of.getUtf8String()))
      .withNetwork(network)
      .withNetworkAliases("keycloak")
      .withEnv(
        Map.of(
          "KAFKA_TOPIC", KAFKA_TOPIC,
          "KAFKA_ADMIN_TOPIC", KAFKA_ADMIN_TOPIC,
          "KAFKA_BOOTSTRAP_SERVERS",  "redpanda:29092",
          "KAFKA_CLIENT_ID", "event-publisher-test"
        ))
      .withProviderClassesFrom("target/classes")
      .withProviderLibsFrom(dependencies)
      .withRealmImportFile("test-realm.json");
    KEYCLOAK_CONTAINER.start();
  }

  // https://github.com/testcontainers/testcontainers-java/blob/master/modules/redpanda/src/main/java/org/testcontainers/redpanda/RedpandaContainer.java
  static class RedpandaContainer extends GenericContainer<RedpandaContainer> {

    private static final String REDPANDA_FULL_IMAGE_NAME = "docker.redpanda.com/vectorized/redpanda";

    private static final DockerImageName REDPANDA_IMAGE = DockerImageName.parse(REDPANDA_FULL_IMAGE_NAME);

    private static final int REDPANDA_PORT = 9092;
    private static final int SCHEMA_REGISTRY_PORT = 8081;

    private static final String STARTER_SCRIPT = "/testcontainers_start.sh";

    public RedpandaContainer(String image) {
      this(DockerImageName.parse(image));
    }

    public RedpandaContainer(DockerImageName imageName) {
      super(imageName);
      imageName.assertCompatibleWith(REDPANDA_IMAGE);

      boolean isLessThanBaseVersion = new ComparableVersion(imageName.getVersionPart()).isLessThan("v22.2.1");
      if (REDPANDA_FULL_IMAGE_NAME.equals(imageName.getUnversionedPart()) && isLessThanBaseVersion) {
        throw new IllegalArgumentException("Redpanda version must be >= v22.2.1");
      }

      withExposedPorts(REDPANDA_PORT, SCHEMA_REGISTRY_PORT);
      withCreateContainerCmdModifier(cmd -> {
        cmd.withEntrypoint("sh");
      });
      waitingFor(Wait.forLogMessage(".*Started Kafka API server.*", 1));
      withCommand("-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
      super.containerIsStarting(containerInfo);

      String command = "#!/bin/bash\n";

      command += "/usr/bin/rpk redpanda start --mode dev-container ";
      command += "--kafka-addr PLAINTEXT://0.0.0.0:29092,OUTSIDE://0.0.0.0:9092 ";
      command += "--advertise-kafka-addr PLAINTEXT://redpanda:29092,OUTSIDE://" + getHost() + ":" + getMappedPort(9092);

      String config = "";

      copyFileToContainer(Transferable.of(command.getBytes(StandardCharsets.UTF_8), 0777), STARTER_SCRIPT);
    }

    public String getBootstrapServers() {
      return String.format("PLAINTEXT://%s:%s", getHost(), getMappedPort(REDPANDA_PORT));
    }
  }


  @BeforeAll
  static void setup() {
    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    var eventConfig = realm.getRealmEventsConfig();
    eventConfig.setEventsListeners(List.of("prox-events-kafka-publisher"));
    realm.updateRealmEventsConfig(eventConfig);
  }

  @Test
  void shouldPublishAdminEvent() throws IOException {
    Consumer<String, String> consumer = buildConsumer();
    consumer.subscribe(List.of(KAFKA_ADMIN_TOPIC));
    consumer.seekToBeginning(consumer.assignment());

    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    // Publish event
    var groupRepresentation = new GroupRepresentation();
    groupRepresentation.setName("test-group");
    realm.groups().add(groupRepresentation);

    AdminEvent groupEvent = null;
    var objectMapper = new ObjectMapper();
    int i = 30;
    try {
      while(i >= 0) {
        if(i == 0 && groupEvent != null) {
          throw new RuntimeException("Could not consume admin event in time");
        }

        var records = consumer.poll(Duration.of(1000, TimeUnit.MILLISECONDS.toChronoUnit()));
        for(var record : records) {
          var adminEvent = objectMapper.readValue(record.value(), AdminEvent.class);
          if(adminEvent.getOperationType() == OperationType.CREATE) {
            groupEvent = adminEvent;
            i = -1;
            break;
          }
        }

        consumer.commitAsync();
        --i;
      }
    } finally {
      consumer.close();
    }

    assertThat(groupEvent).isNotNull();
  }

  @Test
  void shouldPublishEvent() throws IOException {
    Consumer<String, String> consumer = buildConsumer();
    consumer.subscribe(List.of(KAFKA_TOPIC));
    consumer.seekToBeginning(consumer.assignment());

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


    Event clientLoginEvent = null;
    var objectMapper = new ObjectMapper();
    int i = 30;
    try {
      while(i >= 0) {
        if(i == 0 && clientLoginEvent != null) {
          throw new RuntimeException("Could not consume admin event in time");
        }

        var records = consumer.poll(Duration.of(1000, TimeUnit.MILLISECONDS.toChronoUnit()));
        for(var record : records) {
          var event = objectMapper.readValue(record.value(), Event.class);
          if(event.getType() == EventType.CLIENT_LOGIN) {
            clientLoginEvent = event;
            i = -1;
            break;
          }
        }

        consumer.commitAsync();
        --i;
      }
    } finally {
      consumer.close();
    }

    assertThat(clientLoginEvent).isNotNull();
  }

  <K, V> Consumer<K, V> buildConsumer() {
    return new KafkaConsumer<>(
      Map.of(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA_CONTAINER.getHost() + ":" + REDPANDA_CONTAINER.getMappedPort(9092),
        ConsumerConfig.GROUP_ID_CONFIG, "test-consumer",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
    ));
  }

}
