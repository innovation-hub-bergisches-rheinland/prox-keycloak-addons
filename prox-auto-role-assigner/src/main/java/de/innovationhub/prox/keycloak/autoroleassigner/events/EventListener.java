package de.innovationhub.prox.keycloak.autoroleassigner.events;

import org.keycloak.events.Event;

public interface EventListener {
  default void onAction(Event event) {
  }
}
