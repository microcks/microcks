# Microcks 2.0 with Docker Compose

Microcks 2.0 needs no Keycloak and no Kafka: MongoDB and the Postman runtime are the only dependencies of the app, and
the async minion receives its events through a WebSocket.

| File | Purpose |
| --- | --- |
| `docker-compose.yml` | One app instance, MongoDB, Postman runtime. |
| `async-addon.yml` | Adds the async minion (WebSocket mocks, `config/application.properties`). |
| `kafka-addon.yml`, `nats-addon.yml`, `rabbitmq-addon.yml`, `kafdrop-addon.yml` | Brokers to mock Kafka, NATS or AMQP services. |
| `docker-compose-cluster.yml` | Two app replicas clustered by the Hazelcast event bus (`--profile async` adds the minion). |

```bash
docker compose up -d                                                  # app only
docker compose -f docker-compose.yml -f async-addon.yml up -d         # with the async minion
docker compose -f docker-compose.yml -f async-addon.yml -f kafka-addon.yml up -d   # and a Kafka mock broker
docker compose -f docker-compose-cluster.yml --profile async up -d    # two replicas
```

The app images are built from `v2/app/src/main/docker/Dockerfile.jvm` and `v2/minions/async/src/main/docker`.

## Clustering

Replicas relay service updates and AsyncAPI triggers to each other, so a minion connected to any of them receives all
the events, in order. Settings (environment variables of the app):

- `MICROCKS_CLUSTER_DISCOVERY`: `standalone` (default, no discovery), `tcp-ip` or `kubernetes`.
- `MICROCKS_CLUSTER_MEMBERS`: `host:port` list of the replicas for `tcp-ip` (Hazelcast port 5701).
- `KUBERNETES_NAMESPACE`, `MICROCKS_CLUSTER_SERVICE_NAME` or `MICROCKS_CLUSTER_SERVICE_DNS` for `kubernetes`.
- `MICROCKS_CLUSTER_NAME`, `MICROCKS_CLUSTER_PORT`.

Events are not stored: a minion resynchronizes with the REST API each time it connects.

## Authentication

By default Microcks runs **without authentication**: every caller is an anonymous user holding all roles (user,
manager, admin). To enforce it, set `MICROCKS_AUTH_ENABLED=true` on the app. Microcks then validates its own signed
JWT (stateless, no Keycloak):

- **Users** log in through any OIDC provider (Okta, Entra ID, Keycloak, Dex...) configured with
  `MICROCKS_AUTH_OIDC_ENABLED=true`, `MICROCKS_AUTH_OIDC_AUTHORIZATION_URL`, `MICROCKS_AUTH_OIDC_TOKEN_URL`,
  `MICROCKS_AUTH_OIDC_CLIENT_ID` / `_CLIENT_SECRET`, `MICROCKS_AUTH_OIDC_PUBLIC_URL` (callback is
  `<public-url>/api/auth/callback/oidc`) and `MICROCKS_AUTH_OIDC_ALLOWED_REDIRECT_URIS` (the web UI callback, for
  example `http://localhost:3000/api/auth/callback`). The provider token is only used at login.
- **Minions, CLI and CI** use a service account (`client_credentials` on `POST /api/auth/token`). Declare one for the
  minion with `MICROCKS_AUTH_BOOTSTRAP_CLIENT_ID` / `_CLIENT_SECRET` on the app and the same values in
  `MICROCKS_SERVICEACCOUNT` / `MICROCKS_SERVICEACCOUNT_CREDENTIALS` on the minion (see `async-addon.yml`). Others can
  be managed by admins on `/api/service-accounts`.
- Set `MICROCKS_AUTH_PRIVATE_KEY_LOCATION` / `_PUBLIC_KEY_LOCATION` (PEM) so that tokens survive restarts and are shared
  by all replicas; otherwise an ephemeral key is generated and a warning is logged.
- The WebSocket endpoints used by minions require the same Bearer token.
