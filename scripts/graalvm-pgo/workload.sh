#!/usr/bin/env bash
# scripts/graalvm-pgo/workload.sh
#
# Drives one stream of invocations at the washa Lambda binary THROUGH the Lambda Runtime Interface
# Emulator (RIE) while the instrumented native binary captures PGO profile data. washa ships as a
# quarkus-amazon-lambda-http handler (a Lambda poll loop, not an HTTP server), so the only faithful
# way to load it locally is to invoke it the way prod does: POST Function-URL event envelopes to
# RIE's invoke endpoint. The binary unmarshals the event, runs the app, and returns a response
# envelope — exercising the exact hot paths the shipped binary runs (event adapter, JWT validation,
# Jackson, the salary->net engine, formula evaluator, brackets, debt amortization).
#
# Auth: a Keycloak bearer token (password grant), attached to each event's Authorization header. The
# %pgo OIDC tenant is `hybrid`, so it accepts bearer tokens. Sanitized payloads — no real data.
#
# Discipline: best-effort per invocation (a transient RIE hiccup logs and continues rather than
# aborting the stream) so the run captures maximum profile volume; the token mint must succeed.

set -uo pipefail

INVOKE="${RIE_INVOKE:-http://localhost:8080/2015-03-31/functions/function/invocations}"
KC_HOST="${KC_HOST:-http://localhost:8180}"
KC_REALM="${KC_REALM:-washa-pgo}"
KC_USER="${KC_USER:-washa-pgo-1}"
KC_USER_PASSWORD="${KC_USER_PASSWORD:-tester-password}"
KC_CLIENT_ID="${PGO_OIDC_CLIENT_ID:-washa-pgo}"
KC_CLIENT_SECRET="${PGO_OIDC_CLIENT_SECRET:-pgo-client-secret-change-me}"
LOOP_SECONDS="${LOOP_SECONDS:-60}"
# Each worker writes a distinct year_month so concurrent PUT /month invocations don't collide on
# budget_month's unique constraint.
WORKER_MONTH="${WORKER_MONTH:-2027-01}"

# A realistic two-earner month payload (JP + PH currencies, percentage + formula + bracket
# deductions, a relative goal, a reprice-on-payment mortgage with prepayment). Exercises every hot
# branch of the engine. Example figures only; enum wire tokens verified against the budget enums.
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

# Mint a bearer token (password grant; the %pgo Keycloak client has directAccessGrants enabled). The
# email scope is required — washa's allowlist gate matches on the token's email claim.
TOKEN="$(curl -fsS "$KC_HOST/realms/$KC_REALM/protocol/openid-connect/token" \
    -d grant_type=password -d "client_id=$KC_CLIENT_ID" -d "client_secret=$KC_CLIENT_SECRET" \
    -d "username=$KC_USER" -d "password=$KC_USER_PASSWORD" \
    --data-urlencode 'scope=openid email profile' 2>/dev/null | jq -r '.access_token // empty')"
if [ -z "$TOKEN" ]; then
    echo "workload[$KC_USER]: FAILED to mint bearer token from $KC_HOST/realms/$KC_REALM" >&2
    exit 1
fi

# Build a Function-URL (API Gateway HTTP v2) event envelope. Splits path?query into rawPath +
# rawQueryString. $4=1 attaches the bearer header (0 for the anonymous probe).
event() {
    local method="$1" full="$2" body="$3" auth="${4:-1}"
    local path="${full%%\?*}" qs=""
    [ "$full" != "$path" ] && qs="${full#*\?}"
    local hdr='{"content-type":"application/json"}'
    [ "$auth" = 1 ] && hdr="$(jq -nc --arg t "$TOKEN" '{"content-type":"application/json","authorization":("Bearer "+$t)}')"
    jq -nc --arg p "$path" --arg q "$qs" --arg m "$method" --arg b "$body" --argjson h "$hdr" \
        '{version:"2.0",rawPath:$p,rawQueryString:$q,headers:$h,
          requestContext:{http:{method:$m,path:$p,protocol:"HTTP/1.1",sourceIp:"127.0.0.1"}},
          body:(if $b=="" then null else $b end),isBase64Encoded:false}'
}

# Invoke once through RIE; echo the response envelope's statusCode (or 0 on a transport failure).
invoke() {
    local resp
    resp="$(curl -fsS -XPOST "$INVOKE" -H 'content-type: application/json' \
        -d "$(event "$1" "$2" "${3:-}" "${4:-1}")" 2>/dev/null)" || { echo 0; return 0; }
    printf '%s' "$resp" | jq -r '.statusCode // 0' 2>/dev/null || echo 0
}

echo "workload[$KC_USER]: starting against RIE $INVOKE (month $WORKER_MONTH)"
# Seed this worker's month so the loop's GET /month returns a populated view.
invoke PUT "/api/budget/month/$WORKER_MONTH" "$MONTH_JSON" 1 >/dev/null

end_time=$(( $(date +%s) + LOOP_SECONDS ))
i=0 ok=0
while [ "$(date +%s)" -lt "$end_time" ]; do
    i=$((i + 1))
    invoke GET  "/api/me" '' 1 >/dev/null
    invoke GET  "/api/budget/fx?base=JPY" '' 1 >/dev/null
    [ "$(invoke POST "/api/budget/compute" "$MONTH_JSON" 1)" = "200" ] && ok=$((ok + 1))  # CPU-hot path
    invoke GET  "/api/budget/month/$WORKER_MONTH" '' 1 >/dev/null
    [ $((i % 5)) -eq 0 ] && invoke PUT "/api/budget/month/$WORKER_MONTH" "$MONTH_JSON" 1 >/dev/null
done

echo "workload[$KC_USER]: completed $i iterations ($ok compute 200s) in ${LOOP_SECONDS}s"
echo "workload[$KC_USER]: done"
