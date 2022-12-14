package de.innovationhub.prox.keycloak.autoroleassigner.provider;

import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class AssignerEventListenerProviderFactory implements EventListenerProviderFactory {

  private static final Logger log = Logger.getLogger(AssignerEventListenerProviderFactory.class);

  @Override
  public EventListenerProvider create(KeycloakSession keycloakSession) {
    return new AssignerEventListenerProvider(keycloakSession);
  }

  @Override
  public void init(Scope scope) {

  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

  }

  @Override
  public void close() {
  }

  @Override
  public String getId() {
    return "prox-auto-role-assigner";
  }
}
