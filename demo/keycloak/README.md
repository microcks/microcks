# Demo: Microcks secured by Keycloak

Microcks has no built-in user directory: people sign in through an OIDC identity provider. At the first sign-in a `user`
record is created in Microcks; identity and global roles (`user`, `manager`, `admin`) are refreshed from the token claims
(optional: from the `groups` claim, here with `MICROCKS_AUTH_OIDC_ROLE_GROUP_PREFIX=microcks`, so group `microcks-admin` gives role `admin`; `role-claim`, `admin-group`, `manager-group` are also available) at each sign-in. An admin can disable or remove accounts from **Administration > Users**.

## Run

Prerequisites: Docker, JDK 25, and the built artifacts (`mvn package -pl app,distro/uber -am -DskipTests` in `v2/`, and
`npm run build` in `v2/web-ui/app`).

```sh
./run-demo.sh        # Keycloak :8180, Microcks Uber :18080, web UI :13000
./scenario.sh        # scripted walk-through (curl through the UI server)
./run-demo.sh stop
```

Open http://localhost:13000 and click *Sign in*. Realm `microcks-demo` (Keycloak admin console: `admin` / `admin`):

| user  | password | Keycloak group | email (verified)   |
| ----- | -------- | ---------- | ------------------ |
| alice | alice    | admin      | alice@example.com  |
| bob   | bob      | manager    | laurent.broudoux@gmail.com |
| carol | carol    | user       | carol@example.com  |
| dave  | dave     | user       | dave@example.com   |

## Configuration used

| Variable | Value |
| --- | --- |
| `MICROCKS_AUTH_ENABLED` | `true` |
| `MICROCKS_AUTH_OIDC_ENABLED` | `true` |
| `MICROCKS_AUTH_OIDC_AUTHORIZATION_URL` / `_TOKEN_URL` | Keycloak `.../realms/microcks-demo/protocol/openid-connect/{auth,token}` |
| `MICROCKS_AUTH_OIDC_CLIENT_SECRET` | `microcks-demo-secret` (client `microcks-app`) |
| `MICROCKS_AUTH_OIDC_PUBLIC_URL` | `http://localhost:18080` (the callback registered in Keycloak is `<public-url>/api/auth/callback/oidc`) |
| `MICROCKS_AUTH_OIDC_ALLOWED_REDIRECT_URIS` | `http://localhost:13000/api/auth/callback` (the UI) |

The web UI needs `ORIGIN=http://localhost:13000`: adapter-node 6 otherwise assumes `https` when building the callback URL.
Without `MICROCKS_AUTH_*` signing key settings the backend uses an ephemeral key: sessions do not survive a restart.
