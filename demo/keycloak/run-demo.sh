#!/usr/bin/env bash
# Starts Keycloak (realm microcks-demo), the Microcks Uber (JVM) secured by it, and the web UI.
# Usage: ./run-demo.sh   (stop with ./run-demo.sh stop)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; v2="$here/../.."
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAR="$v2/distro/uber/target/quarkus-app/quarkus-run.jar"
fail() { echo "ERROR: $*" >&2; exit 1; }

if [ "${1:-}" = stop ]; then
  [ -f /tmp/microcks-demo.pids ] && xargs -r kill < /tmp/microcks-demo.pids || true
  rm -f /tmp/microcks-demo.pids; docker compose -f "$here/docker-compose.yml" down; exit 0
fi

[ -f "$JAR" ] || fail "$JAR not found: run 'mvn package -pl app,distro/uber -am -DskipTests' in v2/ first"
[ -f "$v2/web-ui/app/build/index.js" ] || fail "web UI not built: run 'npm run build' in v2/web-ui/app first"
command -v "$JAVA" >/dev/null || fail "java not found: set JAVA_HOME to a JDK 21+"
port_busy() { (exec 3<>"/dev/tcp/localhost/$1") 2>/dev/null; }
port_busy 18080 && fail "port 18080 is already in use"
port_busy 13000 && fail "port 13000 is already in use (stale UI process? find it with: ps aux | grep build/index.js)"
docker compose -f "$here/docker-compose.yml" up -d
until curl -sf http://localhost:8180/realms/microcks-demo/.well-known/openid-configuration >/dev/null; do sleep 3; done

KC=http://localhost:8180/realms/microcks-demo/protocol/openid-connect
MICROCKS_AUTH_ENABLED=true \
MICROCKS_AUTH_OIDC_ENABLED=true \
MICROCKS_AUTH_OIDC_AUTHORIZATION_URL=$KC/auth \
MICROCKS_AUTH_OIDC_TOKEN_URL=$KC/token \
MICROCKS_AUTH_OIDC_CLIENT_ID=microcks-app \
MICROCKS_AUTH_OIDC_CLIENT_SECRET=microcks-demo-secret \
MICROCKS_AUTH_OIDC_ROLE_GROUP_PREFIX=microcks \
MICROCKS_AUTH_OIDC_PUBLIC_URL=http://localhost:18080 \
MICROCKS_AUTH_OIDC_ALLOWED_REDIRECT_URIS=http://localhost:13000/api/auth/callback \
QUARKUS_HTTP_PORT=18080 \
  "$JAVA" -jar "$JAR" > /tmp/microcks-demo-backend.log 2>&1 &
echo $! > /tmp/microcks-demo.pids
for _ in $(seq 1 60); do
  curl -sf http://localhost:18080/api/auth/config >/dev/null && break
  kill -0 "$(head -1 /tmp/microcks-demo.pids)" 2>/dev/null || { tail -20 /tmp/microcks-demo-backend.log; fail "backend exited, see /tmp/microcks-demo-backend.log"; }
  sleep 2
done
curl -sf http://localhost:18080/api/auth/config >/dev/null || fail "backend not ready after 2 minutes"

MICROCKS_API_URL=http://localhost:18080 PORT=13000 ORIGIN=http://localhost:13000 \
  node "$v2/web-ui/app/build/index.js" > /tmp/microcks-demo-ui.log 2>&1 &
echo $! >> /tmp/microcks-demo.pids
echo "UI http://localhost:13000  - Keycloak http://localhost:8180 (admin/admin) - users alice(admin) bob(manager) carol(user) dave(user), password = username"
