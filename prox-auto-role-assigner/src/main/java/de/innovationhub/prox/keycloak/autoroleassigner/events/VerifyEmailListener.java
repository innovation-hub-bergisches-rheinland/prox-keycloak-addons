package de.innovationhub.prox.keycloak.autoroleassigner.events;

import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.models.KeycloakSession;

public class VerifyEmailListener  implements EventListener {

  private final KeycloakSession keycloakSession;
  private final Logger log = Logger.getLogger(VerifyEmailListener.class);
  private static final List<String> PROFESSOR_EMAIL_PATTERNS = List.of("@th-koeln.de", "@fh-koeln.de");

  public VerifyEmailListener(KeycloakSession keycloakSession) {
    this.keycloakSession = keycloakSession;
  }

  @Override
  public void onAction(Event event) {
    var realm = keycloakSession.getContext().getRealm();

    if(realm.getId().equals(event.getRealmId())) {
      // Not our realm
      return;
    }

    var userId = UUID.fromString(event.getUserId());
    var email = event.getDetails().get("email");

    if(email == null || email.isBlank()) {
      log.debug("No email found in event");
      return;
    }

    if(PROFESSOR_EMAIL_PATTERNS.stream().noneMatch(email::endsWith)) {
      log.trace("Not an email from TH-Koeln or FH-Koeln");
      return;
    }

    log.debug("User " + userId + " verified college email account '" + email + "', assigning professor group");
    var user = keycloakSession.users().getUserById(realm, userId.toString());

    if(user == null) {
      log.error("Could not assign group to user, user could not be found");
      return;
    }

    var professorGroup = realm.getGroupsStream().filter(g -> g.getName().equalsIgnoreCase("professor")).findFirst();

    if(professorGroup.isEmpty()) {
      log.error("Could not assign group to user, group could not be found");
      return;
    }

    try {
      user.joinGroup(professorGroup.get());
      log.debug("User " + userId + " was successfully assigned to professor group");
    } catch (Exception e) {
      log.error("Couldn't asign user to professor group", e);
    }
  }
}
