package de.innovationhub.prox.keycloak.autoroleassigner.events;

import java.util.UUID;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.models.KeycloakSession;

public class VerifyEmailListener  implements EventListener {

  private final KeycloakSession keycloakSession;
  private final Logger log = Logger.getLogger(VerifyEmailListener.class);

  public VerifyEmailListener(KeycloakSession keycloakSession) {
    this.keycloakSession = keycloakSession;
  }

  @Override
  public void onAction(Event event) {
    var realm = keycloakSession.realms().getRealm(event.getRealmId());
    var userId = UUID.fromString(event.getUserId());
    var email = event.getDetails().get("email");

    if(email != null && !email.isBlank()) {
      if(email.trim().endsWith("@th-koeln.de") || email.trim().endsWith("@fh-koeln.de")) {
        log.debug("User " + userId + " verified college email account '" + email + "', assigning professor group");
        var user = keycloakSession.users().getUserById(realm, userId.toString());
        var professorGroup = realm.getGroupsStream().filter(g -> g.getName().equalsIgnoreCase("professor")).findFirst();

        if(user != null && professorGroup.isPresent()) {
          try {
            user.joinGroup(professorGroup.get());
            log.debug("User " + userId + " was successfully assigned to professor group");
          } catch (Exception e) {
            log.error("Couldn't asign user to professor group", e);
          }
        } else {
          if(user == null) {
            log.error("Could not assign group to user, user is null");
          }
          if(professorGroup.isEmpty()) {
            log.error("Could not assign group to user, group is null");
          }
        }
      }
    }
  }
}
