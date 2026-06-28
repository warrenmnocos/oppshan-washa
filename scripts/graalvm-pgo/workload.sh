#!/usr/bin/env bash
# scripts/graalvm-pgo/workload.sh
#
# Drives a running washa instance through a steady-state workload so the instrumented native
# binary captures representative PGO profile data. It exercises the CPU-hot paths that dominate a
# budget app: the salary->net engine, the formula evaluator, additive brackets, debt amortization
# (all behind POST /api/budget/compute), plus FX lookups and SPA static serving.
#
# Sanitized: no real identities or figures. Auth uses a bearer token supplied via WASHA_LOAD_TOKEN
# (e.g. minted by the OIDC test server); without it, the authenticated endpoints are skipped and
# the workload still exercises static serving + health.
#
# Discipline: set -euo pipefail; curl --fail-with-body; non-2xx on a required call aborts.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LOOP_SECONDS="${LOOP_SECONDS:-60}"
TOKEN="${WASHA_LOAD_TOKEN:-}"

auth_header=()
if [ -n "$TOKEN" ]; then
  auth_header=(--header "Authorization: Bearer $TOKEN")
fi

# A realistic two-earner month payload (JP + PH currencies, deductions, a goal, a mortgage).
# Example figures only.
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

get() { curl --fail-with-body --silent --show-error "${auth_header[@]}" "$BASE_URL$1" --output /dev/null; }

post_compute() {
  curl --fail-with-body --silent --show-error "${auth_header[@]}" \
    --header 'Content-Type: application/json' \
    --request POST "$BASE_URL/api/budget/compute" --data "$MONTH_JSON" --output /dev/null
}

echo "workload: starting against $BASE_URL (token: $([ -n "$TOKEN" ] && echo yes || echo no))"
end_time=$(( $(date +%s) + LOOP_SECONDS ))
iterations=0
while [ "$(date +%s)" -lt "$end_time" ]; do
  iterations=$((iterations + 1))
  get "/" || true                      # SPA static serving
  get "/q/health" || true
  if [ -n "$TOKEN" ]; then
    post_compute                       # the CPU-hot engine path
    get "/api/budget/fx?base=JPY"
    get "/api/budget/month/2026-06" || true
  fi
done
echo "workload: completed $iterations iterations in ${LOOP_SECONDS}s"
