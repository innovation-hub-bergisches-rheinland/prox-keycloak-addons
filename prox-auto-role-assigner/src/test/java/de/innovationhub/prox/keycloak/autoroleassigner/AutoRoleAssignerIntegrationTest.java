package de.innovationhub.prox.keycloak.autoroleassigner;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.filter.session.SessionFilter;
import java.net.MalformedURLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AutoRoleAssignerIntegrationTest {
  static final String TEST_REALM = "innovation-hub-bergisches-rheinland";

  static final GenericContainer<?> MAILHOG_CONTAINER;
  static final KeycloakContainer KEYCLOAK_CONTAINER;
  static final Integer MAILHOG_SMTP_PORT = 1025;
  static final Integer MAILHOG_HTTP_PORT = 8025;

  static {
    Network network = Network.newNetwork();
    MAILHOG_CONTAINER = new GenericContainer<>("mailhog/mailhog")
      .withExposedPorts(MAILHOG_SMTP_PORT, MAILHOG_HTTP_PORT)
      .withNetwork(network)
      .withNetworkAliases("mailhog")
        .waitingFor(Wait.forLogMessage(".*Creating API v2 with WebPath:.*", 1));
    MAILHOG_CONTAINER.start();

    KEYCLOAK_CONTAINER = new KeycloakContainer()
      .withProviderClassesFrom("target/classes")
      .withNetwork(network)
      .withNetworkAliases("keycloak")
      .withRealmImportFile("test-realm.json");
    KEYCLOAK_CONTAINER.start();
  }

  @BeforeEach
  void setup() {
    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    // First we need to enable our addon
    var eventConfig = realm.getRealmEventsConfig();
    eventConfig.setEventsListeners(List.of("prox-auto-role-assigner"));
    realm.updateRealmEventsConfig(eventConfig);
  }

  // It is really hard to integration test this addon as it requires the VERIFY_EMAIL addon to be
  // fired that is not fired when an admin flips the verification switch for a user.
  // For minimal testing, just test if it is deployable.
  @Test
  void isDeployable() {
  }

  /*
  TODO: For whatever reason this test does not trigger the event.
  @Test
  void shouldAssignProfessorGroup() throws MalformedURLException {
    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();

    // Create a user
    var userRepresentation = new UserRepresentation();
    userRepresentation.setEmail("test@th-koeln.de");
    userRepresentation.setUsername("test");
    userRepresentation.setEnabled(true);
    var response = adminClient.realm(TEST_REALM).users().create(userRepresentation);
    assertThat(response.getStatus()).isEqualTo(201);
    var lSplit = response.getHeaderString("Location").split("/");
    var userId = lSplit[lSplit.length - 1];

    // Send a verification mail to him
    var user = adminClient.realm(TEST_REALM).users().get(userId);
    user.sendVerifyEmail();

    // Check mail inbox
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = MAILHOG_CONTAINER.getMappedPort(MAILHOG_HTTP_PORT);
    RestAssured.basePath = "/api/v2";
    SessionFilter sessionFilter = new SessionFilter();
    CookieFilter cookieFilter = new CookieFilter();

    var mailContent = given()
      .when().get("/messages")
      .then()
      .log().all()
      .body("total", equalTo(1))
      .extract()
      .jsonPath()
      .getString("items[0].Content.Body");

    mailContent = mailContent.replace("\n", "");
    mailContent = mailContent.replace("\r", "");

    // Between those two strings will be the verification link
    var beginString = "Click on the link below to start this process.";
    var beginIndex = mailContent.indexOf(beginString) + beginString.length();
    var endString = "This link will expire within 12 hours.";
    var endIndex = mailContent.indexOf(endString);
    var link = mailContent.substring(beginIndex, endIndex);

    var driver = WebDriverManager.firefoxdriver().create();
    driver.navigate().to(link);

    var navLink = driver.findElement(By.id("kc-info-message")).findElement(By.tagName("a")).getAttribute("href");
    driver.navigate().to(navLink);

    var wait = new WebDriverWait(driver, 5);
    wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));

    driver.quit();

    assertThat(user.groups())
      .anyMatch(g -> g.getName().equals("professor"));
  }*/
}
