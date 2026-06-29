#!/usr/bin/env bash
# scripts/graalvm-pgo/workload.sh
#
# Drives one authenticated washa session through a realistic steady-state workload while the
# instrumented native binary captures PGO profile data. Three phases:
#   1. Bootstrap   — OIDC sign-in (Keycloak code flow), then seed this worker's month
#   2. Steady-state loop — the CPU-hot budget paths, LOOP_SECONDS long
#   3. Teardown    — OIDC sign-out
#
# The hot paths this is built to sample: POST /api/budget/compute (the salary->net engine, formula
# evaluator, additive brackets, debt amortization — all stateless, no DB), plus the per-request
# encrypted-session decrypt that every authenticated /api/** call pays, FX lookups, and a month
# read/write against this worker's own year_month (so concurrent workers never contend on the shared
# budget_month unique key). Sanitized: sample identities and figures only, no real data.
#
# Discipline:
#   set -euo pipefail   — any unhandled failure aborts
#   curl --fail-with-body — non-2xx on a required call aborts the script
#   jq -er              — a missing JSON field aborts the script

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KC_REALM="${KC_REALM:-washa-pgo}"
KC_USER="${KC_USER:-washa-pgo-1}"
KC_USER_PASSWORD="${KC_USER_PASSWORD:-tester-password}"
LOOP_SECONDS="${LOOP_SECONDS:-60}"
COOKIE_JAR="${COOKIE_JAR:-/tmp/washa-pgo-cookies-$$.txt}"
# This worker's own month key — parallel-workload.sh hands each worker a distinct one so the PUT
# path writes non-overlapping budget_month rows (the table has a unique constraint on year_month).
WORKER_MONTH="${WORKER_MONTH:-2027-01}"

cleanup_workload() {
    rm -f "$COOKIE_JAR" /tmp/washa-pgo-html-$$*.html
}
trap cleanup_workload EXIT

# A realistic two-earner month payload (JP + PH currencies, percentage + formula + bracket
# deductions, a relative goal, a reprice-on-payment mortgage with prepayment). Exercises every hot
# branch of the engine. Example figures only. Enum wire tokens verified against the budget enums.
read -r -d '' MONTH_JSON <<'JSON' || true
{
  "salaries": [
    {"name":"Earner A","currency":"JPY","engine":"generic",
     "components":[{"label":"Basic salary","amount":500000,"taxable":true,"basic":true,"varAuto":false}],
     "deductions":[
       {"label":"Pension","type":"deductionType.pct","base":"deductionBase.gross","rate":9.15,"cap":59475,"pretax":true,"varAuto":false},
       {"label":"Health","type":"deductionType.pct","base":"deductionBase.gross","rate":4.95,"cap":68805,"pretax":true,"varAuto":false},
       {"label":"Income tax","type":"deductionType.formula","expr":"round(max(0, (gross*12 - 1200000)) * 0.1 / 12)","pretax":false,"varAuto":false}],
     "variables":[]},
    {"name":"Earner B","currency":"PHP","engine":"generic",
     "components":[{"label":"Basic salary","amount":40000,"taxable":true,"basic":true,"varAuto":false}],
     "deductions":[
       {"label":"SSS","type":"deductionType.pct","base":"deductionBase.basic","rate":5,"cap":1750,"floor":250,"pretax":true,"varAuto":false},
       {"label":"Withholding","type":"deductionType.brackets","base":"deductionBase.taxable","pretax":false,"varAuto":false,
        "brackets":[{"var":"taxable","op":"bracketOp.gt","val":20833,"type":"bracketType.formula","expr":"0.15*(taxable-20833)"}]}],
     "variables":[]}
  ],
  "expenses":[{"label":"Tithe","auto":"tithe","cur":"JPY"},{"label":"Rent","amt":150000,"cur":"JPY"}],
  "goals":[{"label":"Emergency","amt":150000,"cur":"JPY","target":{"type":"goalTargetType.relative","base":"all","mult":6},"savings":true,"wd":0,"closed":false}],
  "debts":[{"name":"Mortgage","principal":5000000,"annualRate":6.5,"monthly":38000,"termMonths":240,
            "repriceMode":"debtRepriceMode.payment","cur":"PHP","prepay":true,"prepayAmt":10000,"prepayCur":"PHP","rateSteps":[]}],
  "cur":[{"code":"JPY","sym":"¥"},{"code":"PHP","sym":"₱"}],
  "fxRates":{"PHP":0.36}
}
JSON

# ----------------------------------------------------------------------
# Helpers
# ----------------------------------------------------------------------

# Wrap curl with diagnostic logging on failure. Without this, workers die silently under concurrent
# load because curl --silent --fail swallows both the response body and the HTTP code, leaving no way
# to tell whether a request timed out, was rejected, or hit a backend exception. Writes the response
# body to stdout on success; on failure dumps rc + http_code + body (first 1 KB) + URL to stderr and
# returns curl's exit code so the caller's `set -e` still aborts.
curl_diag() {
    local body_file rc=0 http_code
    body_file="$(mktemp -p /tmp "washa-pgo-resp-${KC_USER:-anon}-XXXXXX")"
    http_code=$(curl --silent --show-error --fail-with-body \
        --write-out '%{http_code}' --output "$body_file" "$@") || rc=$?
    if [ "$rc" -ne 0 ]; then
        {
            echo "workload[${KC_USER:-?}]: curl FAILED rc=$rc http=$http_code"
            echo "  url: ${*: -1}"
            echo "  body (first 1KB):"
            head -c 1024 "$body_file" 2>/dev/null || true
            echo
        } >&2
        rm -f "$body_file"
        return "$rc"
    fi
    cat "$body_file"
    rm -f "$body_file"
}

api_get() {
    curl_diag --cookie "$COOKIE_JAR" "$BASE_URL$1"
}

api_post_json() {
    curl_diag --cookie "$COOKIE_JAR" \
        --header 'Content-Type: application/json' \
        --request POST "$BASE_URL$1" \
        --data "$2"
}

api_put_json() {
    curl_diag --cookie "$COOKIE_JAR" \
        --header 'Content-Type: application/json' \
        --request PUT "$BASE_URL$1" \
        --data "$2"
}

# ----------------------------------------------------------------------
# OIDC code flow against Keycloak
# ----------------------------------------------------------------------
# washa's GET /sso/sign-in/oidc/google is @Authenticated, so hitting it signed out triggers the
# Quarkus OIDC redirect to Keycloak. We scrape Keycloak's login form, POST credentials, and follow
# the redirect chain back through the callback to "/". This is the only path that exercises washa's
# real session machinery (token exchange + encrypted token-state cookie), which is what we want PGO
# to profile.
oidc_sign_in() {
    local login_html="/tmp/washa-pgo-html-$$-login.html"

    curl --fail-with-body --silent --show-error --location \
        --cookie-jar "$COOKIE_JAR" --cookie "$COOKIE_JAR" \
        "$BASE_URL/sso/sign-in/oidc/google" \
        --output "$login_html"

    local form_action=""
    if command -v xmllint >/dev/null 2>&1; then
        form_action="$(xmllint --html --xpath 'string(//form[@id="kc-form-login"]/@action)' "$login_html" 2>/dev/null || true)"
    fi
    if [ -z "$form_action" ]; then
        # Regex fallback so the script works without libxml2-utils. Keycloak emits id="kc-form-login"
        # before action="..." on the same line.
        form_action="$(grep -oE 'id="kc-form-login"[^>]*action="[^"]+"' "$login_html" \
            | sed -E 's/.*action="([^"]+)".*/\1/' \
            | sed 's/&amp;/\&/g' \
            | head -1)"
    fi

    if [ -z "$form_action" ]; then
        echo "workload[${KC_USER}]: could not extract Keycloak login form action URL" >&2
        head -80 "$login_html" >&2
        return 1
    fi

    curl --fail-with-body --silent --show-error --location \
        --cookie-jar "$COOKIE_JAR" --cookie "$COOKIE_JAR" \
        --data-urlencode "username=$KC_USER" \
        --data-urlencode "password=$KC_USER_PASSWORD" \
        --data-urlencode "credentialId=" \
        "$form_action" \
        --output /dev/null

    # Sanity check: /api/me returns 200 with the account once the session cookie is set, 401 if
    # sign-in silently failed (curl_diag aborts the worker on the 401).
    api_get '/api/me' >/dev/null
}

oidc_sign_out() {
    curl --fail-with-body --silent --show-error --location \
        --cookie "$COOKIE_JAR" \
        "$BASE_URL/sso/sign-out" \
        --output /dev/null || true
}

# ----------------------------------------------------------------------
# Phase 1 — Bootstrap
# ----------------------------------------------------------------------
bootstrap() {
    echo "workload[${KC_USER}]: signing in"
    oidc_sign_in

    # Seed this worker's month so the loop's GET /month returns a populated view rather than racing
    # a not-yet-created month. The write also warms the persist path before it's sampled in the loop.
    echo "workload[${KC_USER}]: seeding month $WORKER_MONTH"
    api_put_json "/api/budget/month/$WORKER_MONTH" "$MONTH_JSON" >/dev/null
}

# ----------------------------------------------------------------------
# Phase 2 — Steady-state loop
# ----------------------------------------------------------------------
steady_state_loop() {
    local end_time=$(( $(date +%s) + LOOP_SECONDS ))
    local i=0

    while [ "$(date +%s)" -lt "$end_time" ]; do
        i=$((i + 1))

        api_get '/api/me' >/dev/null                                   # per-request session decrypt
        api_get '/api/budget/fx?base=JPY' >/dev/null                   # FX lookup
        api_post_json '/api/budget/compute' "$MONTH_JSON" >/dev/null   # the CPU-hot engine path
        api_post_json "/api/budget/compute?month=$WORKER_MONTH" "$MONTH_JSON" >/dev/null  # month-aware compute
        api_get "/api/budget/month/$WORKER_MONTH" >/dev/null           # persisted read + view assembly

        if [ $((i % 5)) -eq 0 ]; then
            api_put_json "/api/budget/month/$WORKER_MONTH" "$MONTH_JSON" >/dev/null  # persist path
        fi
    done

    echo "workload[${KC_USER}]: completed $i iterations in ${LOOP_SECONDS}s"
}

# ----------------------------------------------------------------------
# Phase 3 — Teardown
# ----------------------------------------------------------------------
teardown() {
    echo "workload[${KC_USER}]: signing out"
    oidc_sign_out
}

# ----------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------
echo "workload[${KC_USER}]: starting against $BASE_URL (month $WORKER_MONTH)"
bootstrap
steady_state_loop
teardown
echo "workload[${KC_USER}]: done"
