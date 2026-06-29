#!/usr/bin/env bash
# scripts/graalvm-pgo/keycloak-bootstrap.sh
#
# Idempotently creates the realm, client, and users that the PGO workload signs in as. Safe to
# re-run — uses create-if-missing and drift-correcting update logic so a previous partial run
# doesn't break the next.
#
# washa's prod auth is Google OIDC on the default tenant (provider=google). The %pgo Quarkus
# profile (application.properties) unsets that preset and repoints the default tenant at this
# throwaway Keycloak realm, so the workload can drive the real OIDC code flow — and therefore the
# per-request encrypted-session-decrypt path — without touching Google. The N tester emails created
# here must match the widened %pgo washa.allowed-identities list, or sign-in is allowlist-denied.

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
# "$KC_USER-i@example.com". parallel-workload.sh drives one concurrent OIDC session per user.
KC_USER_COUNT="${KC_USER_COUNT:-1}"
# Access-token lifespan in seconds. The PGO workload runs LOOP_SECONDS=300 per worker; Keycloak's
# 300s default would expire mid-workload for any worker whose first iteration starts late (the
# stagger plus sign-in eats ~20s). Sized to 1.5x LOOP_SECONDS so workers finish before expiry.
KC_ACCESS_TOKEN_LIFESPAN="${KC_ACCESS_TOKEN_LIFESPAN:-450}"
# washa's OIDC redirect-path; the client must whitelist it or the code flow's callback is rejected.
KC_REDIRECT_URI="http://localhost:8080/sso/sign-in/oidc/callback/google"

# Resolve the Keycloak container's name so we can docker-exec kcadm.sh.
KC_CONTAINER="$(docker ps --filter "ancestor=quay.io/keycloak/keycloak:26.5.4" --format '{{.Names}}' | head -1)"
if [ -z "$KC_CONTAINER" ]; then
    echo "keycloak-bootstrap: could not find a running Keycloak container" >&2
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
        -s 'directAccessGrantsEnabled=false' \
        -s 'consentRequired=false' \
        -s "redirectUris=[\"$KC_REDIRECT_URI\"]"
else
    # Keep secret + redirect URI + consent in sync if they drifted. Consent stays off so the workload
    # only ever has to post the single login form, never a separate Grant Access page.
    kcadm update "clients/$CLIENT_UUID" -r "$KC_REALM" \
        -s "secret=$KC_CLIENT_SECRET" \
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
