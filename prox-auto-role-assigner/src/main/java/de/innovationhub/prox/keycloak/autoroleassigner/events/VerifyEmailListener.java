package de.innovationhub.prox.keycloak.autoroleassigner.events;

import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.models.GroupModel;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;

public class VerifyEmailListener  implements EventListener {

  private final KeycloakSession keycloakSession;
  private final Logger log = Logger.getLogger(VerifyEmailListener.class);
  private static final List<String> PROFESSOR_EMAIL_PATTERNS = List.of("@th-koeln.de", "@fh-koeln.de");
  private final RealmProvider realmProvider;

  public VerifyEmailListener(KeycloakSession keycloakSession) {
    this.keycloakSession = keycloakSession;

    this.realmProvider = keycloakSession.realms();
  }

  @Override
  public void onAction(Event event) {
    var realm = realmProvider.getRealm(event.getRealmId());

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

    // If we don't have a transaction yet, create one
    boolean createdTransaction = false;
    if(!this.keycloakSession.getTransactionManager().isActive()) {
      log.debug("No transacation active, beginning one");
      this.keycloakSession.getTransactionManager().begin();
      createdTransaction = true;
    }
    try {
      assignGroup(realm, userId);

      this.keycloakSession.getTransactionManager().commit();
    } catch (Exception e) {
      log.errorf(e, "Could not assign group");

      // Rollback transaction on error
      if(createdTransaction) {
        this.keycloakSession.getTransactionManager().rollback();
      }
    }
  }

  private void assignGroup(RealmModel realm, String userId) {
    var user = this.keycloakSession.users().getUserById(realm, userId);

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
