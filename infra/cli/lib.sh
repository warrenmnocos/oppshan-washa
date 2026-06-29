#!/usr/bin/env bash
# Shared configuration and helpers for the washa AWS CLI provisioner.
#
# Sourced by provision.sh / seed-secrets.sh / set-lambda-env.sh / destroy.sh. It holds the canonical
# names, regions, and sizing that BOTH provisioners (this CLI variant and infra/terraform/) must
# agree on — see docs/superpowers/specs/2026-06-29-aws-deployment-design.md. Keep this in lockstep
# with infra/terraform/variables.tf.
#
# This file is sourced, not executed: it deliberately does NOT set `set -euo pipefail` (the entry
# scripts own that) so sourcing stays side-effect-light.

# The config block below is consumed by the scripts that source this file, so shellcheck cannot see
# the uses when it lints lib.sh on its own.
# shellcheck disable=SC2034

# --- Canonical configuration (mirror of infra/terraform/variables.tf) ---------------------------
AWS_REGION="ap-southeast-1"            # Singapore: Lambda, CloudFront origin, SSM, IAM (co-located with Neon).
ACM_REGION="us-east-1"                 # CloudFront requires its viewer certificate in us-east-1.
FUNCTION_NAME="oppshan-washa"          # cd.yml targets this literal name — keep in sync.
DOMAIN="washa.oppshan.com"            # Public hostname served by CloudFront.
ZONE_NAME="oppshan.com"               # Existing Route 53 hosted zone (looked up, never created).
LAMBDA_MEMORY_MB="256"
LAMBDA_TIMEOUT_S="30"
LAMBDA_RESERVED_CONCURRENCY="5"        # Caps cost and protects Neon's small connection pool.
LOG_RETENTION_DAYS="14"
PRICE_CLASS="PriceClass_200"           # Includes the Tokyo edge; the cost sweet spot.
GITHUB_REPO="warrenmnocos/oppshan-washa"
GITHUB_DEPLOY_REF="refs/heads/main"
SSM_PREFIX="/oppshan/washa"

# Derived / fixed names.
LAMBDA_EXEC_ROLE="${FUNCTION_NAME}-lambda-exec"
GITHUB_DEPLOY_ROLE="${FUNCTION_NAME}-github-deploy"
GITHUB_DEPLOY_POLICY="${FUNCTION_NAME}-github-deploy"   # inline policy name on the deploy role
LOG_GROUP="/aws/lambda/${FUNCTION_NAME}"
OAC_NAME="${FUNCTION_NAME}-oac"
CF_ORIGIN_ID="${FUNCTION_NAME}-lambda-url"
CF_INVOKE_STATEMENT_ID="AllowCloudFrontInvoke"

# GitHub OIDC federation.
GITHUB_OIDC_HOST="token.actions.githubusercontent.com"
GITHUB_OIDC_AUDIENCE="sts.amazonaws.com"
# GitHub's OIDC root-CA thumbprint. AWS validates the certificate chain against its own trust store
# and ignores this value for the well-known GitHub host, but the API field is still accepted; it is
# supplied for compatibility across aws-cli versions.
GITHUB_OIDC_THUMBPRINT="6938fd4d98bab03faadb97b34396831e3780aea1"

# CloudFront's fixed global hosted-zone ID for alias targets (identical for every distribution).
CLOUDFRONT_HOSTED_ZONE_ID="Z2FDTNDATAQYW2"

# Managed CloudFront policy IDs (§3 of the design). The CLI uses literal IDs; Terraform resolves the
# same managed policies by name via data sources.
CF_CACHE_POLICY_CACHING_OPTIMIZED="658327ea-f89d-4fab-a63d-7e88639e58f6"
CF_CACHE_POLICY_CACHING_DISABLED="4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
CF_ORIGIN_REQUEST_POLICY_ALL_VIEWER_EXCEPT_HOST="b689b0a8-53d0-40ab-baf2-68738e2966ac"

# Runtime config → SSM map (mirrors oppshan-files' convention). Index-aligned arrays:
#   SSM_PARAM_PATHS[i] — parameter path: the app prefix + the literal env var name
#   LAMBDA_ENV_VARS[i] — the env var the Lambda gets it as; identical to the SSM suffix
#   ENV_SOURCE_KEYS[i] — the key to read it from in the repo-root .env; identical again, because
#                        application.properties resolves the ${GOOGLE_*}/${TOKEN_*} placeholders, so
#                        prod uses the same names as dev (and as oppshan-files) — no QUARKUS_OIDC_* rename
#   PARAM_TYPES[i]     — SecureString (secrets + PII) or String (the non-secret datasource URL/username,
#                        matching oppshan-files; the password rides separately in its own SecureString)
SSM_PARAM_PATHS=(
  "${SSM_PREFIX}/GOOGLE_CLIENT_ID"
  "${SSM_PREFIX}/GOOGLE_CLIENT_SECRET"
  "${SSM_PREFIX}/TOKEN_ENCRYPTION_SECRET"
  "${SSM_PREFIX}/QUARKUS_DATASOURCE_JDBC_URL"
  "${SSM_PREFIX}/QUARKUS_DATASOURCE_USERNAME"
  "${SSM_PREFIX}/QUARKUS_DATASOURCE_PASSWORD"
  "${SSM_PREFIX}/OPPSHAN_WASHA_ALLOWED_IDENTITIES"
)
LAMBDA_ENV_VARS=(
  "GOOGLE_CLIENT_ID"
  "GOOGLE_CLIENT_SECRET"
  "TOKEN_ENCRYPTION_SECRET"
  "QUARKUS_DATASOURCE_JDBC_URL"
  "QUARKUS_DATASOURCE_USERNAME"
  "QUARKUS_DATASOURCE_PASSWORD"
  "OPPSHAN_WASHA_ALLOWED_IDENTITIES"
)
ENV_SOURCE_KEYS=(
  "GOOGLE_CLIENT_ID"
  "GOOGLE_CLIENT_SECRET"
  "TOKEN_ENCRYPTION_SECRET"
  "QUARKUS_DATASOURCE_JDBC_URL"
  "QUARKUS_DATASOURCE_USERNAME"
  "QUARKUS_DATASOURCE_PASSWORD"
  "OPPSHAN_WASHA_ALLOWED_IDENTITIES"
)
PARAM_TYPES=(
  "SecureString"   # GOOGLE_CLIENT_ID
  "SecureString"   # GOOGLE_CLIENT_SECRET
  "SecureString"   # TOKEN_ENCRYPTION_SECRET
  "String"         # QUARKUS_DATASOURCE_JDBC_URL (no password inline)
  "String"         # QUARKUS_DATASOURCE_USERNAME
  "SecureString"   # QUARKUS_DATASOURCE_PASSWORD
  "SecureString"   # OPPSHAN_WASHA_ALLOWED_IDENTITIES (PII)
)

# --- Helpers ------------------------------------------------------------------------------------

# Log to stderr so stdout stays clean for captured command output / final machine-readable outputs.
log() { printf '[washa] %s\n' "$*" >&2; }
die() { printf '[washa] ERROR: %s\n' "$*" >&2; exit 1; }

# Verify every named command is on PATH; abort listing all that are missing.
require_cmds() {
  local missing=0 c
  for c in "$@"; do
    if ! command -v "$c" >/dev/null 2>&1; then
      printf '[washa] ERROR: required command not found: %s\n' "$c" >&2
      missing=1
    fi
  done
  [ "$missing" -eq 0 ] || die "install the missing prerequisite(s) and retry"
}

# Caller's AWS account ID, cached after the first lookup.
account_id() {
  if [ -z "${_WASHA_ACCOUNT_ID:-}" ]; then
    _WASHA_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text --no-cli-pager)" \
      || die "could not resolve the AWS account id — are credentials configured?"
  fi
  printf '%s' "$_WASHA_ACCOUNT_ID"
}
