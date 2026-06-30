#!/usr/bin/env bash
# Tear down the washa stack created by provision.sh, in reverse dependency order. DESTRUCTIVE.
#
# Guarded: requires typing 'yes' interactively, or pass --force to skip the prompt. Best-effort —
# resources that are already gone are skipped, so a partial teardown can be re-run. The existing
# Route 53 hosted zone for oppshan.com is never deleted (provision.sh only looks it up).
#
# Note: disabling and deleting a CloudFront distribution can take ~15 minutes (it must fully deploy
# the disabled state first); this script waits for it.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# lib.sh is a sibling resolved at runtime; point shellcheck at it and don't flag the dynamic path.
# shellcheck source=lib.sh disable=SC1091
source "$SCRIPT_DIR/lib.sh"

require_cmds aws jq

FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

ACCOUNT_ID="$(account_id)"
OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/${GITHUB_OIDC_HOST}"

cat >&2 <<EOF
About to DESTROY the washa stack in account ${ACCOUNT_ID} (${AWS_REGION}; ACM in ${ACM_REGION}):
  - Route 53 A/AAAA + ACM validation records for ${DOMAIN}  (zone ${ZONE_NAME} is kept)
  - CloudFront distribution for ${DOMAIN}, OAC ${OAC_NAME}, ACM certificate
  - Lambda ${FUNCTION_NAME} (+ Function URL, CloudFront permission), log group ${LOG_GROUP}
  - IAM roles ${LAMBDA_EXEC_ROLE} and ${GITHUB_DEPLOY_ROLE}, GitHub OIDC provider
  - the 10 ${SSM_PREFIX}/* SSM parameters, INCLUDING any seeded secret values
EOF
if [ "$FORCE" -ne 1 ]; then
  printf 'Type "yes" to proceed: ' >&2
  read -r confirm
  [ "$confirm" = "yes" ] || die "aborted"
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

# Run an aws command best-effort: succeed silently if the resource is already absent; warn (but keep
# going) on any other failure, so one stuck resource never blocks the rest of the teardown.
tolerant() {
  local out rc
  set +e
  out="$("$@" 2>&1)"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] && return 0
  if printf '%s' "$out" | grep -qiE 'NotFound|NoSuch|does not exist|not found'; then
    return 0
  fi
  log "WARNING (continuing): $* :: ${out}"
  return 0
}

# Resolve the hosted zone (best-effort) so record deletions can run.
ZONE_ID="$(aws route53 list-hosted-zones-by-name --dns-name "$ZONE_NAME" \
  --query "HostedZones[?Name=='${ZONE_NAME}.'].Id | [0]" --output text --no-cli-pager 2>/dev/null || true)"
ZONE_ID="${ZONE_ID#/hostedzone/}"

# Delete a Route 53 record by name (without trailing dot) and type, if it exists.
delete_record() {
  local name="$1" type="$2" rrs batch
  { [ -n "$ZONE_ID" ] && [ "$ZONE_ID" != "None" ]; } || { log "  no zone; skip ${type} ${name}"; return 0; }
  rrs="$(aws route53 list-resource-record-sets --hosted-zone-id "$ZONE_ID" \
    --query "ResourceRecordSets[?Name=='${name}.' && Type=='${type}'] | [0]" \
    --output json --no-cli-pager 2>/dev/null || true)"
  if [ -z "$rrs" ] || [ "$rrs" = "null" ]; then
    log "  no ${type} record for ${name}"
    return 0
  fi
  batch="$WORKDIR/del-${type}.json"
  RRS="$rrs" jq -n '{Comment: "washa teardown", Changes: [{Action: "DELETE", ResourceRecordSet: ($ENV.RRS | fromjson)}]}' > "$batch"
  tolerant aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
    --change-batch "file://$batch" --no-cli-pager
  log "  deleted ${type} ${name}"
}

# ── 1 · Route 53 public alias (A/AAAA) ──────────────────────────────────────────────────────────
log "1 Route 53 alias A/AAAA for ${DOMAIN}"
delete_record "$DOMAIN" "A"
delete_record "$DOMAIN" "AAAA"

# ── 2 · CloudFront distribution (disable, wait, delete) ─────────────────────────────────────────
log "2 CloudFront distribution"
DIST_ID="$(aws cloudfront list-distributions --no-cli-pager 2>/dev/null \
  | jq -r --arg d "$DOMAIN" 'first(.DistributionList.Items[]? | select((.Aliases.Items // []) | index($d)) | .Id) // empty')"
if [ -n "$DIST_ID" ]; then
  cfg="$(aws cloudfront get-distribution-config --id "$DIST_ID" --no-cli-pager)"
  etag="$(printf '%s' "$cfg" | jq -r .ETag)"
  if [ "$(printf '%s' "$cfg" | jq -r .DistributionConfig.Enabled)" = "true" ]; then
    printf '%s' "$cfg" | jq '.DistributionConfig | .Enabled = false' > "$WORKDIR/disable.json"
    aws cloudfront update-distribution --id "$DIST_ID" \
      --distribution-config "file://$WORKDIR/disable.json" --if-match "$etag" --no-cli-pager >/dev/null
    log "  disabling ${DIST_ID}; waiting to deploy (can take ~15 min)…"
    aws cloudfront wait distribution-deployed --id "$DIST_ID"
    etag="$(aws cloudfront get-distribution-config --id "$DIST_ID" --query ETag --output text --no-cli-pager)"
  fi
  tolerant aws cloudfront delete-distribution --id "$DIST_ID" --if-match "$etag" --no-cli-pager
  log "  deleted ${DIST_ID}"
else
  log "  none for ${DOMAIN}"
fi

# ── 3 · Lambda permission for CloudFront ────────────────────────────────────────────────────────
log "3 Lambda CloudFront permission"
tolerant aws lambda remove-permission --function-name "$FUNCTION_NAME" \
  --statement-id "$CF_INVOKE_STATEMENT_ID" --no-cli-pager

# ── 4 · ACM certificate + its validation record ─────────────────────────────────────────────────
log "4 ACM certificate ${DOMAIN} (${ACM_REGION})"
CERT_ARN="$(aws acm list-certificates --region "$ACM_REGION" \
  --query "CertificateSummaryList[?DomainName=='${DOMAIN}'].CertificateArn | [0]" \
  --output text --no-cli-pager 2>/dev/null || true)"
if [ -n "$CERT_ARN" ] && [ "$CERT_ARN" != "None" ]; then
  rr="$(aws acm describe-certificate --region "$ACM_REGION" --certificate-arn "$CERT_ARN" \
    --query "Certificate.DomainValidationOptions[0].ResourceRecord" --output json --no-cli-pager 2>/dev/null || true)"
  if [ -n "$rr" ] && [ "$rr" != "null" ]; then
    vname="$(printf '%s' "$rr" | jq -r .Name)"
    vtype="$(printf '%s' "$rr" | jq -r .Type)"
    delete_record "${vname%.}" "$vtype"
  fi
  tolerant aws acm delete-certificate --region "$ACM_REGION" --certificate-arn "$CERT_ARN"
  log "  deleted certificate"
else
  log "  none"
fi

# ── 5 · Origin Access Control ───────────────────────────────────────────────────────────────────
log "5 Origin Access Control ${OAC_NAME}"
OAC_ID="$(aws cloudfront list-origin-access-controls \
  --query "OriginAccessControlList.Items[?Name=='${OAC_NAME}'].Id | [0]" \
  --output text --no-cli-pager 2>/dev/null || true)"
if [ -n "$OAC_ID" ] && [ "$OAC_ID" != "None" ]; then
  oac_etag="$(aws cloudfront get-origin-access-control --id "$OAC_ID" \
    --query ETag --output text --no-cli-pager 2>/dev/null || true)"
  tolerant aws cloudfront delete-origin-access-control --id "$OAC_ID" --if-match "$oac_etag" --no-cli-pager
  log "  deleted ${OAC_ID}"
else
  log "  none"
fi

# ── 6 · Lambda Function URL + function ──────────────────────────────────────────────────────────
log "6 Lambda Function URL + function ${FUNCTION_NAME}"
tolerant aws lambda delete-function-url-config --function-name "$FUNCTION_NAME" --no-cli-pager
tolerant aws lambda delete-function --function-name "$FUNCTION_NAME" --no-cli-pager

# ── 7 · Log group ───────────────────────────────────────────────────────────────────────────────
log "7 log group ${LOG_GROUP}"
tolerant aws logs delete-log-group --log-group-name "$LOG_GROUP" --no-cli-pager

# ── 8 · IAM execution role (inline "logs" policy must go before the role) ─────────────────────────
log "8 execution role ${LAMBDA_EXEC_ROLE}"
tolerant aws iam delete-role-policy --role-name "$LAMBDA_EXEC_ROLE" \
  --policy-name logs --no-cli-pager
tolerant aws iam delete-role --role-name "$LAMBDA_EXEC_ROLE" --no-cli-pager

# ── 9 · IAM deploy role ─────────────────────────────────────────────────────────────────────────
log "9 deploy role ${GITHUB_DEPLOY_ROLE}"
tolerant aws iam delete-role-policy --role-name "$GITHUB_DEPLOY_ROLE" \
  --policy-name "$GITHUB_DEPLOY_POLICY" --no-cli-pager
tolerant aws iam delete-role --role-name "$GITHUB_DEPLOY_ROLE" --no-cli-pager

# ── 10 · GitHub OIDC provider ───────────────────────────────────────────────────────────────────
log "10 GitHub OIDC provider"
tolerant aws iam delete-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" --no-cli-pager

# ── 11 · SSM parameters (including any seeded secret values) ─────────────────────────────────────
log "11 SSM parameters under ${SSM_PREFIX}"
for path in "${SSM_PARAM_PATHS[@]}"; do
  tolerant aws ssm delete-parameter --name "$path" --no-cli-pager
done

log "teardown complete"
