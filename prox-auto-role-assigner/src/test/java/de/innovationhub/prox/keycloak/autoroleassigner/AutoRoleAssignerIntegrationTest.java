package de.innovationhub.prox.keycloak.autoroleassigner;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AutoRoleAssignerIntegrationTest {
  static final String TEST_REALM = "innovation-hub-bergisches-rheinland";

  @Container
  KeycloakContainer keycloakContainer = new KeycloakContainer()
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
