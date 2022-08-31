package de.innovationhub.prox.keycloak.autoroleassigner.provider;

import de.innovationhub.prox.keycloak.autoroleassigner.utils.KeycloakUtils;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerTransaction;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.GroupModel;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public class AssignerEventListenerProvider implements EventListenerProvider {

  private static final Logger log = Logger.getLogger(AssignerEventListenerProvider.class);
  private final KeycloakSession keycloakSession;
  private static final List<String> PROFESSOR_EMAIL_PATTERNS = List.of("@th-koeln.de", "@fh-koeln.de");
  private final EventListenerTransaction tx;

  public AssignerEventListenerProvider(KeycloakSession keycloakSession) {
    this.keycloakSession = keycloakSession;
    this.tx = new EventListenerTransaction(null, this::onAction);
    keycloakSession.getTransactionManager().enlist(tx);
  }

  @Override
  public void onEvent(Event event) {
    log.debug("onEvent: " + KeycloakUtils.eventToString(event));
    log.debug("Transaction state: " + this.keycloakSession.getTransactionManager().isActive());

    if(event.getType() == EventType.VERIFY_EMAIL) {
      log.debug("Scheduling Assignment transaction");
      this.onAction(event);
      // this.tx.addEvent(event);
    }
  }

  private void onAction(Event event) {
    var realm = keycloakSession.realms().getRealm(event.getRealmId());

    var email = event.getDetails().get("email");

    if (email == null || email.isBlank()) {
      log.debug("No email found in event");
      return;
    }

    if (PROFESSOR_EMAIL_PATTERNS.stream().noneMatch(email::endsWith)) {
      log.trace("Not an email from TH-Koeln or FH-Koeln");
      return;
    }
    var userId = event.getUserId();

    log.debug("User " + userId + " verified college email account '" + email
      + "', assigning professor group");
    assignGroup(realm, userId);
  }

  private void assignGroup(RealmModel realm, String userId) {
    var user = this.keycloakSession.users().getUserById(realm, userId);

    if (user == null) {
      log.error("Could not assign group to user, user could not be found");
      return;
    }

    GroupProvider groupProvider = this.keycloakSession.groups();
    Optional<GroupModel> professorGroup = groupProvider.getGroupsStream(realm)
      .filter(g -> g.getName().equalsIgnoreCase("professor")).findFirst();

    if (professorGroup.isEmpty()) {
      log.error("Could not assign group to user, group could not be found");
      return;
    }

    user.joinGroup(professorGroup.get());
    log.debug("User " + userId + " was successfully assigned to professor group");
  }

  @Override
  public void close() {

  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {
    // Auto-generated method stub
  }
}
