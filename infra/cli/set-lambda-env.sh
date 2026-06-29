#!/usr/bin/env bash
# Read the seven SSM SecureString values (decrypted) and apply them as the washa Lambda's
# environment, each under its QUARKUS_* / OPPSHAN_WASHA_ALLOWED_IDENTITIES name. Shared by both provisioners
# — run after seed-secrets.sh.
#
# Secret values are never echoed. They are passed to the AWS API via a temp file written with a
# restrictive umask and removed on exit; secrets are kept out of process argument lists by handing
# them to jq through the environment rather than --arg.
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# lib.sh is a sibling resolved at runtime; point shellcheck at it and don't flag the dynamic path.
# shellcheck source=lib.sh disable=SC1091
source "$SCRIPT_DIR/lib.sh"

require_cmds aws jq
account_id >/dev/null   # fail fast if credentials are missing

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

vars_json='{}'
for i in "${!SSM_PARAM_PATHS[@]}"; do
  path="${SSM_PARAM_PATHS[$i]}"
  name="${LAMBDA_ENV_VARS[$i]}"
  if ! val="$(aws ssm get-parameter --name "$path" --with-decryption \
      --query Parameter.Value --output text --no-cli-pager 2>/dev/null)"; then
    die "missing SSM parameter ${path} — run provision.sh then seed-secrets.sh first"
  fi
  [ "$val" != "REPLACE_ME" ] || die "${path} is still REPLACE_ME — run seed-secrets.sh first"
  # Fold this value into the accumulator. Both the accumulator (which already holds earlier secret
  # values) and the new value are passed through the environment so they never appear in argv.
  vars_json="$(ACC="$vars_json" VAL="$val" jq -n --arg k "$name" \
    '($ENV.ACC | fromjson) + {($k): $ENV.VAL}')"
  log "loaded ${name}"
done

VARS="$vars_json" jq -n '{Variables: ($ENV.VARS | fromjson)}' > "$WORKDIR/env.json"
aws lambda update-function-configuration \
  --function-name "$FUNCTION_NAME" \
  --environment "file://$WORKDIR/env.json" \
  --no-cli-pager >/dev/null
aws lambda wait function-updated-v2 --function-name "$FUNCTION_NAME"
log "applied ${#SSM_PARAM_PATHS[@]} environment variables to ${FUNCTION_NAME}"
