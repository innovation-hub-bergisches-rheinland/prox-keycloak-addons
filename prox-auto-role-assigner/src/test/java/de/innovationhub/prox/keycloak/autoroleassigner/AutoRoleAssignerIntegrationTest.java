package de.innovationhub.prox.keycloak.autoroleassigner;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AutoRoleAssignerIntegrationTest {
  static final String TEST_REALM = "innovation-hub-bergisches-rheinland";
  static final Logger LOGGER = LoggerFactory.getLogger(AutoRoleAssignerIntegrationTest.class);

  @Container
  KeycloakContainer keycloakContainer = new KeycloakContainer()
    .withLogConsumer(new Slf4jLogConsumer(LOGGER))
    .withProviderClassesFrom("target/classes")
    .withRealmImportFile("test-realm.json");

  // It is really hard to integration test this addon as it requires the VERIFY_EMAIL addon to be
  // fired that is not fired when an admin flips the verification switch for a user.
  // For minimal testing, just test if it is deployable.
  @Test
  void isDeployable() {
    Keycloak adminClient = keycloakContainer.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    // First we need to enable our addon
    var eventConfig = realm.getRealmEventsConfig();
    eventConfig.setEventsListeners(List.of("prox-auto-role-assigner"));
    realm.updateRealmEventsConfig(eventConfig);
  }
}
