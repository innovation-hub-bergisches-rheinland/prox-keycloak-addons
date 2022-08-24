package de.innovationhub.prox.keycloak.autoroleassigner.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AuthDetails;

public class KeycloakUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final Logger log = Logger.getLogger(KeycloakUtils.class);

  public static String eventToString(Event event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize event, use fallback", e);
    }

    return String.format(
      "Event (type: %s, realm: %s, client: %s, error: %s, ip: %s, session: %s, time: %d, user: %s, details: %s)",
      event.getType(), event.getRealmId(), event.getClientId(), event.getError(),
      event.getIpAddress(), event.getSessionId(), event.getTime(), event.getUserId(),
      event.getDetails());
  }
}
