package de.innovationhub.prox.keycloak.autoroleassigner.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerTransaction;
import org.keycloak.models.GroupModel;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmProvider;

public class VerifyEmailListener  implements EventListener {

  private final KeycloakSession keycloakSession;
  private final Logger log = Logger.getLogger(VerifyEmailListener.class);
  private static final List<String> PROFESSOR_EMAIL_PATTERNS = List.of("@th-koeln.de", "@fh-koeln.de");
  private EventListenerTransaction tx = new EventListenerTransaction(null, this::assignRole);

  public VerifyEmailListener(KeycloakSession keycloakSession) {
    this.keycloakSession = keycloakSession;

    this.keycloakSession.getTransactionManager().enlistAfterCompletion(tx);
  }

  @Override
  public void onAction(Event event) {
    var realm = keycloakSession.getContext().getRealm();

    if (!realm.getId().equals(event.getRealmId())) {
      // Not our realm
      log.debugf("Event and session realm not matching (Event Realm: %s, Session realm: %s)",
        event.getRealmId(), realm.getId());
      return;
    }

    var email = event.getDetails().get("email");

    if (email == null || email.isBlank()) {
      log.debug("No email found in event");
      return;
    }

    if (PROFESSOR_EMAIL_PATTERNS.stream().noneMatch(email::endsWith)) {
      log.trace("Not an email from TH-Koeln or FH-Koeln");
      return;
    }

    tx.addEvent(event);
  }

  private void assignRole(Event event) {
    var realm = keycloakSession.getContext().getRealm();
    var userId = UUID.fromString(event.getUserId());
    var email = event.getDetails().get("email");

    log.debug("User " + userId + " verified college email account '" + email
      + "', assigning professor group");
    var user = keycloakSession.users().getUserById(realm, userId.toString());

    if (user == null) {
      log.error("Could not assign group to user, user could not be found");
      return;
    }

    GroupProvider groupProvider = keycloakSession.groups();
    Optional<GroupModel> professorGroup = groupProvider.getGroupsStream(realm)
      .filter(g -> g.getName().equalsIgnoreCase("professor")).findFirst();

    if (professorGroup.isEmpty()) {
      log.error("Could not assign group to user, group could not be found");
      return;
    }

    user.joinGroup(professorGroup.get());
    log.debug("User " + userId + " was successfully assigned to professor group");
  }
}
