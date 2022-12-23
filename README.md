# PROX Keycloak Addons

These Keycloak Addons belong to PROX and adhere various cases here.

## Plugins

### Auto Role Assigner

Automatically assigns roles to users. Currently the role for permitting users to act as a supervisor
in PROX are assigned once a user verifies a E-Mail address from a TH Köln Domain.

### Kafka Event Publisher

Automatically publishes Keycloak events to Kafka. Needs the following Environment variables in
Keycloak. Events will be serialized to JSON.

| Variable                | Description             |
| ----------------------- | ----------------------- |
| KAFKA_TOPIC             | Topic for Client Events |
| KAFKA_CLIENT_ID         | Producer Client ID      |
| KAFKA_BOOTSTRAP_SERVERS | Kafka Servers           |
| KAFKA_ADMIN_TOPIC       | Topic for Admin Events  |


### Redis Event Publisher

Automatically publishes Keycloak events to Redis. 
Currently using PUBLISH command, but we keep in mind that it might be worth to use XADD in the future.
Needs the following Environment variables in Keycloak. Events will be serialized to JSON.

| Variable                           | Description                    |
| ---------------------------------- | ------------------------------ |
| REDIS_HOST                         | Redis Host                     |
| REDIS_PORT                         | Redis Port                     |
| REDIS_KEYCLOAK_EVENT_CHANNEL       | Channel for Events             |
| REDIS_KEYCLOAK_ADMIN_EVENT_CHANNEL | Channel for Admin Events       |

## Release

```
./mvnw release:clean release:prepare
```
