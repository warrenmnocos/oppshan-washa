#!/usr/bin/env bash
# scripts/graalvm-pgo/keycloak-bootstrap.sh
#
# Idempotently creates the realm, client, and users that the PGO workload signs in as. Safe to
# re-run — uses create-if-missing and drift-correcting update logic so a previous partial run
# doesn't break the next.
#
# washa's prod auth is Google OIDC on the default tenant (provider=google). The %pgo Quarkus profile
# (application.properties) unsets that preset and repoints the default tenant (as `hybrid`) at this
# throwaway Keycloak realm, so the workload can mint bearer tokens here (direct-access / password
# grant) and invoke the binary under RIE without touching Google. The N tester emails created here
# must match the widened %pgo washa.allowed-identities list, or the token's email is allowlist-denied.

set -euo pipefail

KC_HOST_PORT="${KC_HOST_PORT:-8180}"
KC_HOST="http://localhost:${KC_HOST_PORT}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin}"
KC_REALM="${KC_REALM:-washa-pgo}"
KC_CLIENT_ID="${KC_CLIENT_ID:-washa-pgo}"
KC_CLIENT_SECRET="${PGO_OIDC_CLIENT_SECRET:-pgo-client-secret-change-me}"
KC_USER="${KC_USER:-washa-pgo}"
KC_USER_PASSWORD="${KC_USER_PASSWORD:-tester-password}"
# When KC_USER_COUNT >= 2, create N users named "$KC_USER-1" .. "$KC_USER-N", each with email
# "$KC_USER-i@example.com". parallel-workload.sh mints a bearer token per user to drive invocations.
KC_USER_COUNT="${KC_USER_COUNT:-1}"
# Access-token lifespan in seconds. The PGO workload runs LOOP_SECONDS=300 per worker; Keycloak's
# 300s default would expire mid-workload for a worker whose token was minted at the start. Sized to
# 1.5x LOOP_SECONDS so a single minted token outlasts the worker's run.
KC_ACCESS_TOKEN_LIFESPAN="${KC_ACCESS_TOKEN_LIFESPAN:-450}"
# washa's OIDC redirect-path; the client must whitelist it or the code flow's callback is rejected.
KC_REDIRECT_URI="http://localhost:8080/sso/sign-in/oidc/callback/google"

# Resolve THIS pipeline's Keycloak via the compose project (robust to any other keycloak containers
# the host happens to be running), so kcadm.sh runs against the right one.
COMPOSE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-compose.yml"
KC_CONTAINER="$(docker compose -f "$COMPOSE_FILE" ps -q keycloak 2>/dev/null | head -1)"
if [ -z "$KC_CONTAINER" ]; then
    echo "keycloak-bootstrap: PGO compose keycloak not running (docker compose -f $COMPOSE_FILE ps)" >&2
    exit 1
fi

kcadm() {
    docker exec "$KC_CONTAINER" /opt/keycloak/bin/kcadm.sh "$@"
}

echo "keycloak-bootstrap: logging in to $KC_HOST as $KC_ADMIN_USER"
kcadm config credentials \
    --server "$KC_HOST" \
    --realm master \
    --user "$KC_ADMIN_USER" \
    --password "$KC_ADMIN_PASSWORD"

echo "keycloak-bootstrap: ensuring realm $KC_REALM exists with accessTokenLifespan=${KC_ACCESS_TOKEN_LIFESPAN}s"
if ! kcadm get "realms/$KC_REALM" >/dev/null 2>&1; then
    kcadm create realms \
        -s "realm=$KC_REALM" \
        -s "enabled=true" \
        -s "accessTokenLifespan=$KC_ACCESS_TOKEN_LIFESPAN"
else
    # Keep accessTokenLifespan in sync if it drifted (e.g. the realm pre-existed from a prior run
    # before the lifespan was bumped).
    kcadm update "realms/$KC_REALM" \
        -s "accessTokenLifespan=$KC_ACCESS_TOKEN_LIFESPAN"
fi

echo "keycloak-bootstrap: ensuring client $KC_CLIENT_ID exists"
CLIENT_UUID="$(kcadm get clients -r "$KC_REALM" -q "clientId=$KC_CLIENT_ID" --fields id --format csv --noquotes 2>/dev/null | tail -1 || true)"
if [ -z "$CLIENT_UUID" ]; then
    kcadm create clients -r "$KC_REALM" \
        -s "clientId=$KC_CLIENT_ID" \
        -s "secret=$KC_CLIENT_SECRET" \
        -s 'protocol=openid-connect' \
        -s 'publicClient=false' \
        -s 'standardFlowEnabled=true' \
        -s 'directAccessGrantsEnabled=true' \
        -s 'consentRequired=false' \
        -s "redirectUris=[\"$KC_REDIRECT_URI\"]"
else
    # Keep secret + redirect URI + grants in sync if they drifted.
    kcadm update "clients/$CLIENT_UUID" -r "$KC_REALM" \
        -s "secret=$KC_CLIENT_SECRET" \
        -s 'directAccessGrantsEnabled=true' \
        -s 'consentRequired=false' \
        -s "redirectUris=[\"$KC_REDIRECT_URI\"]"
fi

ensure_user() {
    local username="$1" email="$2"
    echo "keycloak-bootstrap: ensuring user $username exists (emailVerified=true)"
    local user_uuid
    user_uuid="$(kcadm get users -r "$KC_REALM" -q "username=$username" --fields id --format csv --noquotes 2>/dev/null | tail -1 || true)"
    if [ -z "$user_uuid" ]; then
        kcadm create users -r "$KC_REALM" \
            -s "username=$username" \
            -s "email=$email" \
            -s 'emailVerified=true' \
            -s "firstName=$username" \
            -s "lastName=PGO" \
            -s 'enabled=true'
        kcadm set-password -r "$KC_REALM" --username "$username" --new-password "$KC_USER_PASSWORD"
    fi
}

if [ "$KC_USER_COUNT" -le 1 ]; then
    ensure_user "${KC_USER}-1" "${KC_USER}-1@example.com"
else
    for n in $(seq 1 "$KC_USER_COUNT"); do
        ensure_user "${KC_USER}-${n}" "${KC_USER}-${n}@example.com"
    done
fi

echo "keycloak-bootstrap: done"
