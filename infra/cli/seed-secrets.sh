#!/usr/bin/env bash
# Push the seven washa runtime values into SSM as SecureString parameters under /oppshan/washa/*. Shared by
# both provisioners — run after provision.sh (CLI) or `terraform apply` (Terraform).
#
# Source of values, in order of preference:
#   1. a dotenv-style values file: an explicit path as $1 (or $WASHA_ENV_FILE), otherwise the
#      repo-root gitignored .env that Quarkus reads in dev. Point it at a prod-only file such as
#      .env.prod to keep prod values out of the dev .env. Read literally, never modified;
#   2. otherwise, interactive prompts (secrets and PII are read silently).
#
# Secret values are never echoed and never written to disk by this script.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# lib.sh is a sibling resolved at runtime; point shellcheck at it and don't flag the dynamic path.
# shellcheck source=lib.sh disable=SC1091
source "$SCRIPT_DIR/lib.sh"

require_cmds aws
account_id >/dev/null   # fail fast if credentials are missing

REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# Values file: an explicit path as $1 (or $WASHA_ENV_FILE), else the repo-root .env. A relative path
# resolves against the repo root, so the script works from any cwd.
ENV_FILE_ARG="${1:-${WASHA_ENV_FILE:-.env}}"
case "$ENV_FILE_ARG" in
  /*) ENV_FILE="$ENV_FILE_ARG" ;;
  *)  ENV_FILE="$REPO_ROOT/$ENV_FILE_ARG" ;;
esac

# Read a dotenv value literally (no shell expansion, no quote stripping) to match how Quarkus loads
# the same .env: the value is everything after the first '=' on the last matching line. Returns
# non-zero if the key is absent.
env_get() {
  local key="$1" file="$2" line
  line="$(grep -E "^[[:space:]]*${key}=" "$file" | tail -n1)" || return 1
  line="${line#"${line%%[![:space:]]*}"}"   # strip any leading whitespace
  line="${line%$'\r'}"                        # tolerate CRLF line endings
  printf '%s' "${line#*=}"
}

declare -a VALUES
if [ -f "$ENV_FILE" ]; then
  log "reading values from ${ENV_FILE} (not modified)"
  for i in "${!ENV_SOURCE_KEYS[@]}"; do
    key="${ENV_SOURCE_KEYS[$i]}"
    if ! VALUES[i]="$(env_get "$key" "$ENV_FILE")" || [ -z "${VALUES[i]}" ]; then
      die "missing or empty ${key} in ${ENV_FILE}"
    fi
  done
else
  log "no .env at ${ENV_FILE}; prompting for the 7 values"
  for i in "${!SSM_PARAM_PATHS[@]}"; do
    prompt="${LAMBDA_ENV_VARS[$i]} (-> ${SSM_PARAM_PATHS[$i]})"
    if [ "${PARAM_TYPES[$i]}" = "SecureString" ]; then
      read -rs -p "  ${prompt}: " val
      printf '\n' >&2
    else
      read -r -p "  ${prompt}: " val
    fi
    [ -n "$val" ] || die "empty value for ${SSM_PARAM_PATHS[$i]}"
    VALUES[i]="$val"
  done
fi

for i in "${!SSM_PARAM_PATHS[@]}"; do
  aws ssm put-parameter \
    --name "${SSM_PARAM_PATHS[$i]}" \
    --type "${PARAM_TYPES[$i]}" \
    --overwrite \
    --value "${VALUES[i]}" \
    --no-cli-pager >/dev/null
  log "seeded ${SSM_PARAM_PATHS[$i]}"
done

log "done — next: bash infra/cli/set-lambda-env.sh"
