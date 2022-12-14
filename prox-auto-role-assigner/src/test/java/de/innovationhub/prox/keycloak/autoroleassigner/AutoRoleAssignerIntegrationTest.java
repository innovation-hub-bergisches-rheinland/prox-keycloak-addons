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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.events.EventType;
import org.keycloak.representations.idm.UserRepresentation;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AutoRoleAssignerIntegrationTest {
  static final String TEST_REALM = "master";
  static final Logger log = org.slf4j.LoggerFactory.getLogger(AutoRoleAssignerIntegrationTest.class);

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

    KEYCLOAK_CONTAINER = new KeycloakContainer("quay.io/keycloak/keycloak:19.0.1")
      .withProviderClassesFrom("target/classes")
      .withNetwork(network)
      .withLogConsumer(new Slf4jLogConsumer(log))
      .withNetworkAliases("keycloak");
      //.withRealmImportFile("test-realm.json");
    KEYCLOAK_CONTAINER.start();
  }

  @BeforeEach
  void setup() {
    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    RealmResource realm = adminClient.realm(TEST_REALM);

    // Update SMTP
    var rep = realm.toRepresentation();
    var smtpSettings = new HashMap<String, String>();
    smtpSettings.put("password", "s3cr3t");
    smtpSettings.put("starttls", "false");
    smtpSettings.put("port", "1025");
    smtpSettings.put("auth", "true");
    smtpSettings.put("host", "mailhog");
    smtpSettings.put("from", "admin@keycloak");
    smtpSettings.put("fromDisplayName", "Test Keycloak");
    smtpSettings.put("ssl", "false");
    smtpSettings.put("user", "john.doe");
    rep.setSmtpServer(smtpSettings);
    realm.update(rep);

    // First we need to enable our addon
    var eventConfig = realm.getRealmEventsConfig();
    eventConfig.setEventsListeners(List.of("prox-auto-role-assigner"));
    eventConfig.setEnabledEventTypes(List.of(EventType.VERIFY_EMAIL.name()));
    realm.updateRealmEventsConfig(eventConfig);
  }

  // It is really hard to integration test this addon as it requires the VERIFY_EMAIL addon to be
  // fired that is not fired when an admin flips the verification switch for a user.
  // For minimal testing, just test if it is deployable.
  @Test
  void isDeployable() {
  }


  /*TODO: For whatever reason this test does not trigger the event.*/
  /*@Test
  void shouldAssignProfessorGroup() throws MalformedURLException {
    Keycloak adminClient = KEYCLOAK_CONTAINER.getKeycloakAdminClient();

    // Create a user
    var userRepresentation = new UserRepresentation();
    userRepresentation.setEmail("test@th-koeln.de");
    userRepresentation.setUsername("test");
    userRepresentation.setEnabled(true);
    userRepresentation.setRequiredActions(List.of("VERIFY_EMAIL"));
    userRepresentation.setEmailVerified(false);
    var response = adminClient.realm(TEST_REALM).users().create(userRepresentation);
    assertThat(response.getStatus()).isEqualTo(201);
    var lSplit = response.getHeaderString("Location").split("/");
    var userId = lSplit[lSplit.length - 1];

    // TODO: Register instead of sending verification mail

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

    var userResource = adminClient.realm(TEST_REALM).users().get(userId);
    var actualUser = userResource.toRepresentation();
    assertThat(actualUser.isEmailVerified()).isTrue();
    assertThat(userResource.groups())
      .anyMatch(group -> group.getName().equals("professor"));
  }*/
}
