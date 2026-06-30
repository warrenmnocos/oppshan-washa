#!/usr/bin/env bash
# Stand up the entire washa production stack with the aws CLI, in dependency order. This creates the
# SAME infrastructure as infra/terraform/ — run one provisioner, not both.
#
# Idempotent: every step checks for the existing resource and creates only what is missing, so a
# partial or interrupted run can simply be re-run. It never overwrites already-seeded SSM secret
# values or the deployed Lambda code.
#
# Order: exec role → log group → Lambda (placeholder) → Function URL → OAC → ACM cert (us-east-1) →
# Route 53 validation + wait → CloudFront → Lambda permission → Route 53 alias → GitHub OIDC + deploy
# role → SSM slots.
#
# Contract: docs/superpowers/specs/2026-06-29-aws-deployment-design.md
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# lib.sh is a sibling resolved at runtime; point shellcheck at it and don't flag the dynamic path.
# shellcheck source=lib.sh disable=SC1091
source "$SCRIPT_DIR/lib.sh"

require_cmds aws jq zip

ACCOUNT_ID="$(account_id)"
log "account ${ACCOUNT_ID} · region ${AWS_REGION} (ACM in ${ACM_REGION})"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

LAMBDA_ARN="arn:aws:lambda:${AWS_REGION}:${ACCOUNT_ID}:function:${FUNCTION_NAME}"
OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/${GITHUB_OIDC_HOST}"

# ── 1/12 · Lambda execution role (CloudWatch Logs only) ─────────────────────────────────────────
log "1/12 execution role ${LAMBDA_EXEC_ROLE}"
if aws iam get-role --role-name "$LAMBDA_EXEC_ROLE" --no-cli-pager >/dev/null 2>&1; then
  log "  exists"
else
  cat > "$WORKDIR/lambda-trust.json" <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Principal": { "Service": "lambda.amazonaws.com" }, "Action": "sts:AssumeRole" }
  ]
}
JSON
  aws iam create-role \
    --role-name "$LAMBDA_EXEC_ROLE" \
    --assume-role-policy-document "file://$WORKDIR/lambda-trust.json" \
    --description "washa Lambda execution role (CloudWatch Logs only)" \
    --no-cli-pager >/dev/null
  aws iam wait role-exists --role-name "$LAMBDA_EXEC_ROLE"
  log "  created"
fi
# CloudWatch Logs permission via a least-privilege inline policy scoped to this function's log group
# (mirrors the inline policy in infra/terraform/lambda.tf). put-role-policy is idempotent.
cat > "$WORKDIR/lambda-logs.json" <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "WriteLogs",
      "Effect": "Allow",
      "Action": ["logs:CreateLogStream", "logs:PutLogEvents"],
      "Resource": "arn:aws:logs:${AWS_REGION}:${ACCOUNT_ID}:log-group:${LOG_GROUP}:*"
    }
  ]
}
JSON
aws iam put-role-policy --role-name "$LAMBDA_EXEC_ROLE" \
  --policy-name logs \
  --policy-document "file://$WORKDIR/lambda-logs.json" --no-cli-pager
EXEC_ROLE_ARN="$(aws iam get-role --role-name "$LAMBDA_EXEC_ROLE" --query Role.Arn --output text --no-cli-pager)"

# ── 2/12 · Log group ────────────────────────────────────────────────────────────────────────────
log "2/12 log group ${LOG_GROUP} (${LOG_RETENTION_DAYS}d)"
existing_lg="$(aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" \
  --query "logGroups[?logGroupName=='${LOG_GROUP}'] | length(@)" --output text --no-cli-pager)"
if [ "$existing_lg" = "0" ]; then
  aws logs create-log-group --log-group-name "$LOG_GROUP" --no-cli-pager
  log "  created"
else
  log "  exists"
fi
aws logs put-retention-policy --log-group-name "$LOG_GROUP" \
  --retention-in-days "$LOG_RETENTION_DAYS" --no-cli-pager

# ── 3/12 · Lambda function (placeholder code) ────────────────────────────────────────────────────
log "3/12 Lambda function ${FUNCTION_NAME}"
if aws lambda get-function --function-name "$FUNCTION_NAME" --no-cli-pager >/dev/null 2>&1; then
  log "  exists (code stays with cd.yml; env stays with set-lambda-env.sh)"
else
  # Tiny placeholder bootstrap so the function exists before any native build. The first real deploy
  # (cd.yml's update-function-code) replaces it; re-runs of this script never touch the code again.
  cat > "$WORKDIR/bootstrap" <<'SH'
#!/bin/sh
# Placeholder bootstrap for the provided.al2023 custom runtime. Exists only so the Lambda can be
# created before the GraalVM-native arm64 binary is built; cd.yml replaces it on the first deploy.
echo '{"statusCode":503,"headers":{"content-type":"text/plain"},"body":"washa is provisioned but not yet deployed"}'
SH
  chmod +x "$WORKDIR/bootstrap"
  ( cd "$WORKDIR" && zip -q placeholder.zip bootstrap )
  # A freshly created IAM role can take a few seconds to become assumable by Lambda; retry on that
  # specific error only.
  attempt=1
  until aws lambda create-function \
      --function-name "$FUNCTION_NAME" \
      --runtime provided.al2023 \
      --architectures arm64 \
      --role "$EXEC_ROLE_ARN" \
      --handler not.used.in.provided.runtime \
      --zip-file "fileb://$WORKDIR/placeholder.zip" \
      --memory-size "$LAMBDA_MEMORY_MB" \
      --timeout "$LAMBDA_TIMEOUT_S" \
      --no-cli-pager >/dev/null 2>"$WORKDIR/createfn.err"; do
    if [ "$attempt" -ge 5 ]; then
      die "create-function failed after ${attempt} attempts: $(cat "$WORKDIR/createfn.err")"
    fi
    if grep -q 'cannot be assumed by Lambda' "$WORKDIR/createfn.err"; then
      log "  waiting for IAM role propagation (attempt ${attempt})"
      sleep 5
      attempt=$((attempt + 1))
    else
      die "create-function failed: $(cat "$WORKDIR/createfn.err")"
    fi
  done
  aws lambda wait function-active-v2 --function-name "$FUNCTION_NAME"
  log "  created"
fi
# Reserved concurrency — best-effort. A new AWS account caps total concurrency at 10, leaving no room
# to reserve any (AWS keeps the unreserved pool >= 10); the account cap then bounds the function anyway.
# Warn and continue if the account can't support it; raise the Lambda concurrency quota to enable it.
if aws lambda put-function-concurrency \
    --function-name "$FUNCTION_NAME" \
    --reserved-concurrent-executions "$LAMBDA_RESERVED_CONCURRENCY" \
    --no-cli-pager >/dev/null 2>&1; then
  log "  reserved concurrency=${LAMBDA_RESERVED_CONCURRENCY}"
else
  log "  WARN: reserved concurrency=${LAMBDA_RESERVED_CONCURRENCY} not set (account concurrency limit too low); the account cap bounds the function. Raise the Lambda quota to enable it."
fi

# ── 4/12 · Function URL (AWS_IAM) ────────────────────────────────────────────────────────────────
log "4/12 Function URL (AWS_IAM)"
if aws lambda get-function-url-config --function-name "$FUNCTION_NAME" --no-cli-pager >/dev/null 2>&1; then
  log "  exists"
else
  aws lambda create-function-url-config \
    --function-name "$FUNCTION_NAME" \
    --auth-type AWS_IAM \
    --no-cli-pager >/dev/null
  log "  created"
fi
FUNCTION_URL="$(aws lambda get-function-url-config --function-name "$FUNCTION_NAME" \
  --query FunctionUrl --output text --no-cli-pager)"
# CloudFront's origin wants the host only — drop the scheme and trailing slash.
ORIGIN_DOMAIN="${FUNCTION_URL#https://}"
ORIGIN_DOMAIN="${ORIGIN_DOMAIN%/}"
log "  origin ${ORIGIN_DOMAIN}"

# ── 5/12 · Origin Access Control ─────────────────────────────────────────────────────────────────
log "5/12 Origin Access Control ${OAC_NAME}"
OAC_ID="$(aws cloudfront list-origin-access-controls \
  --query "OriginAccessControlList.Items[?Name=='${OAC_NAME}'].Id | [0]" \
  --output text --no-cli-pager 2>/dev/null || true)"
if [ -z "$OAC_ID" ] || [ "$OAC_ID" = "None" ]; then
  cat > "$WORKDIR/oac.json" <<JSON
{
  "Name": "${OAC_NAME}",
  "Description": "washa CloudFront to Lambda Function URL (sigv4)",
  "SigningProtocol": "sigv4",
  "SigningBehavior": "always",
  "OriginAccessControlOriginType": "lambda"
}
JSON
  OAC_ID="$(aws cloudfront create-origin-access-control \
    --origin-access-control-config "file://$WORKDIR/oac.json" \
    --query OriginAccessControl.Id --output text --no-cli-pager)"
  log "  created ${OAC_ID}"
else
  log "  exists ${OAC_ID}"
fi

# ── 6/12 · ACM certificate (us-east-1, DNS-validated) ────────────────────────────────────────────
log "6/12 ACM certificate ${DOMAIN} (${ACM_REGION})"
CERT_ARN="$(aws acm list-certificates --region "$ACM_REGION" \
  --query "CertificateSummaryList[?DomainName=='${DOMAIN}'].CertificateArn | [0]" \
  --output text --no-cli-pager 2>/dev/null || true)"
if [ -z "$CERT_ARN" ] || [ "$CERT_ARN" = "None" ]; then
  CERT_ARN="$(aws acm request-certificate \
    --region "$ACM_REGION" \
    --domain-name "$DOMAIN" \
    --validation-method DNS \
    --idempotency-token washa \
    --query CertificateArn --output text --no-cli-pager)"
  log "  requested ${CERT_ARN}"
else
  log "  exists ${CERT_ARN}"
fi

# ── 7/12 · Route 53 zone lookup + ACM validation record + wait for issuance ──────────────────────
log "7/12 Route 53 DNS validation"
ZONE_ID="$(aws route53 list-hosted-zones-by-name --dns-name "$ZONE_NAME" \
  --query "HostedZones[?Name=='${ZONE_NAME}.'].Id | [0]" --output text --no-cli-pager)"
{ [ -n "$ZONE_ID" ] && [ "$ZONE_ID" != "None" ]; } || die "hosted zone ${ZONE_NAME} not found"
ZONE_ID="${ZONE_ID#/hostedzone/}"
log "  zone ${ZONE_NAME} -> ${ZONE_ID}"

# ACM publishes the validation record asynchronously; poll until it appears.
VALIDATION_JSON="null"
attempt=0
while [ "$attempt" -lt 30 ]; do
  VALIDATION_JSON="$(aws acm describe-certificate --region "$ACM_REGION" --certificate-arn "$CERT_ARN" \
    --query "Certificate.DomainValidationOptions[0].ResourceRecord" --output json --no-cli-pager)"
  { [ -n "$VALIDATION_JSON" ] && [ "$VALIDATION_JSON" != "null" ]; } && break
  attempt=$((attempt + 1))
  sleep 5
done
{ [ -n "$VALIDATION_JSON" ] && [ "$VALIDATION_JSON" != "null" ]; } \
  || die "ACM validation record did not appear in time — re-run provision.sh"
V_NAME="$(printf '%s' "$VALIDATION_JSON" | jq -r .Name)"
V_TYPE="$(printf '%s' "$VALIDATION_JSON" | jq -r .Type)"
V_VALUE="$(printf '%s' "$VALIDATION_JSON" | jq -r .Value)"
cat > "$WORKDIR/validation-rr.json" <<JSON
{
  "Comment": "washa ACM DNS validation",
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${V_NAME}",
        "Type": "${V_TYPE}",
        "TTL": 300,
        "ResourceRecords": [ { "Value": "${V_VALUE}" } ]
      }
    }
  ]
}
JSON
aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --change-batch "file://$WORKDIR/validation-rr.json" --no-cli-pager >/dev/null
log "  upserted validation record; waiting for certificate issuance…"
aws acm wait certificate-validated --region "$ACM_REGION" --certificate-arn "$CERT_ARN"
log "  certificate issued"

# ── 8/12 · CloudFront distribution ───────────────────────────────────────────────────────────────
log "8/12 CloudFront distribution"
DIST_ID="$(aws cloudfront list-distributions --no-cli-pager 2>/dev/null \
  | jq -r --arg d "$DOMAIN" 'first(.DistributionList.Items[]? | select((.Aliases.Items // []) | index($d)) | .Id) // empty')"
if [ -z "$DIST_ID" ]; then
  CALLER_REF="washa-$(date +%s)"
  cat > "$WORKDIR/dist-config.json" <<JSON
{
  "CallerReference": "${CALLER_REF}",
  "Aliases": { "Quantity": 1, "Items": ["${DOMAIN}"] },
  "DefaultRootObject": "",
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "${CF_ORIGIN_ID}",
        "DomainName": "${ORIGIN_DOMAIN}",
        "OriginPath": "",
        "CustomHeaders": {
          "Quantity": 2,
          "Items": [
            { "HeaderName": "X-Forwarded-Host", "HeaderValue": "${DOMAIN}" },
            { "HeaderName": "X-Forwarded-Proto", "HeaderValue": "https" }
          ]
        },
        "CustomOriginConfig": {
          "HTTPPort": 80,
          "HTTPSPort": 443,
          "OriginProtocolPolicy": "https-only",
          "OriginSslProtocols": { "Quantity": 1, "Items": ["TLSv1.2"] },
          "OriginReadTimeout": ${LAMBDA_TIMEOUT_S},
          "OriginKeepaliveTimeout": 5
        },
        "OriginAccessControlId": "${OAC_ID}",
        "ConnectionAttempts": 3,
        "ConnectionTimeout": 10,
        "OriginShield": { "Enabled": false }
      }
    ]
  },
  "OriginGroups": { "Quantity": 0 },
  "DefaultCacheBehavior": {
    "TargetOriginId": "${CF_ORIGIN_ID}",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 2,
      "Items": ["GET", "HEAD"],
      "CachedMethods": { "Quantity": 2, "Items": ["GET", "HEAD"] }
    },
    "Compress": true,
    "SmoothStreaming": false,
    "FieldLevelEncryptionId": "",
    "CachePolicyId": "${CF_CACHE_POLICY_CACHING_OPTIMIZED}",
    "OriginRequestPolicyId": "${CF_ORIGIN_REQUEST_POLICY_ALL_VIEWER_EXCEPT_HOST}",
    "LambdaFunctionAssociations": { "Quantity": 0 },
    "FunctionAssociations": { "Quantity": 0 },
    "TrustedSigners": { "Enabled": false, "Quantity": 0 },
    "TrustedKeyGroups": { "Enabled": false, "Quantity": 0 }
  },
  "CacheBehaviors": {
    "Quantity": 2,
    "Items": [
      {
        "PathPattern": "/api/*",
        "TargetOriginId": "${CF_ORIGIN_ID}",
        "ViewerProtocolPolicy": "redirect-to-https",
        "AllowedMethods": {
          "Quantity": 7,
          "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
          "CachedMethods": { "Quantity": 2, "Items": ["GET", "HEAD"] }
        },
        "Compress": false,
        "SmoothStreaming": false,
        "FieldLevelEncryptionId": "",
        "CachePolicyId": "${CF_CACHE_POLICY_CACHING_DISABLED}",
        "OriginRequestPolicyId": "${CF_ORIGIN_REQUEST_POLICY_ALL_VIEWER_EXCEPT_HOST}",
        "LambdaFunctionAssociations": { "Quantity": 0 },
        "FunctionAssociations": { "Quantity": 0 },
        "TrustedSigners": { "Enabled": false, "Quantity": 0 },
        "TrustedKeyGroups": { "Enabled": false, "Quantity": 0 }
      },
      {
        "PathPattern": "/sso/*",
        "TargetOriginId": "${CF_ORIGIN_ID}",
        "ViewerProtocolPolicy": "redirect-to-https",
        "AllowedMethods": {
          "Quantity": 7,
          "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
          "CachedMethods": { "Quantity": 2, "Items": ["GET", "HEAD"] }
        },
        "Compress": false,
        "SmoothStreaming": false,
        "FieldLevelEncryptionId": "",
        "CachePolicyId": "${CF_CACHE_POLICY_CACHING_DISABLED}",
        "OriginRequestPolicyId": "${CF_ORIGIN_REQUEST_POLICY_ALL_VIEWER_EXCEPT_HOST}",
        "LambdaFunctionAssociations": { "Quantity": 0 },
        "FunctionAssociations": { "Quantity": 0 },
        "TrustedSigners": { "Enabled": false, "Quantity": 0 },
        "TrustedKeyGroups": { "Enabled": false, "Quantity": 0 }
      }
    ]
  },
  "Comment": "washa",
  "PriceClass": "${PRICE_CLASS}",
  "Enabled": true,
  "ViewerCertificate": {
    "ACMCertificateArn": "${CERT_ARN}",
    "SSLSupportMethod": "sni-only",
    "MinimumProtocolVersion": "TLSv1.2_2021"
  },
  "Restrictions": { "GeoRestriction": { "RestrictionType": "none", "Quantity": 0 } },
  "HttpVersion": "http2and3",
  "IsIPV6Enabled": true
}
JSON
  create_out="$(aws cloudfront create-distribution \
    --distribution-config "file://$WORKDIR/dist-config.json" --no-cli-pager)"
  DIST_ID="$(printf '%s' "$create_out" | jq -r .Distribution.Id)"
  DIST_ARN="$(printf '%s' "$create_out" | jq -r .Distribution.ARN)"
  DIST_DOMAIN="$(printf '%s' "$create_out" | jq -r .Distribution.DomainName)"
  log "  created ${DIST_ID}"
else
  get_out="$(aws cloudfront get-distribution --id "$DIST_ID" --no-cli-pager)"
  DIST_ARN="$(printf '%s' "$get_out" | jq -r .Distribution.ARN)"
  DIST_DOMAIN="$(printf '%s' "$get_out" | jq -r .Distribution.DomainName)"
  log "  exists ${DIST_ID}"
fi

# ── 9/12 · Lambda permission for CloudFront (source-arn = distribution ARN) ───────────────────────
log "9/12 Lambda permission for cloudfront.amazonaws.com"
existing_policy="$(aws lambda get-policy --function-name "$FUNCTION_NAME" \
  --query Policy --output text --no-cli-pager 2>/dev/null || true)"
if printf '%s' "$existing_policy" | grep -q "$CF_INVOKE_STATEMENT_ID"; then
  log "  exists"
else
  aws lambda add-permission \
    --function-name "$FUNCTION_NAME" \
    --statement-id "$CF_INVOKE_STATEMENT_ID" \
    --action lambda:InvokeFunctionUrl \
    --principal cloudfront.amazonaws.com \
    --source-arn "$DIST_ARN" \
    --function-url-auth-type AWS_IAM \
    --no-cli-pager >/dev/null
  log "  added"
fi

# ── 10/12 · Route 53 A + AAAA alias → CloudFront ────────────────────────────────────────────────
log "10/12 Route 53 alias A/AAAA -> CloudFront"
cat > "$WORKDIR/alias-rr.json" <<JSON
{
  "Comment": "washa public alias to CloudFront",
  "Changes": [
    { "Action": "UPSERT", "ResourceRecordSet": {
        "Name": "${DOMAIN}", "Type": "A",
        "AliasTarget": { "HostedZoneId": "${CLOUDFRONT_HOSTED_ZONE_ID}", "DNSName": "${DIST_DOMAIN}", "EvaluateTargetHealth": false } } },
    { "Action": "UPSERT", "ResourceRecordSet": {
        "Name": "${DOMAIN}", "Type": "AAAA",
        "AliasTarget": { "HostedZoneId": "${CLOUDFRONT_HOSTED_ZONE_ID}", "DNSName": "${DIST_DOMAIN}", "EvaluateTargetHealth": false } } }
  ]
}
JSON
aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --change-batch "file://$WORKDIR/alias-rr.json" --no-cli-pager >/dev/null
log "  upserted"

# ── 11/12 · GitHub OIDC provider + deploy role + least-privilege policy ──────────────────────────
log "11/12 GitHub OIDC provider + deploy role ${GITHUB_DEPLOY_ROLE}"
if aws iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" --no-cli-pager >/dev/null 2>&1; then
  log "  OIDC provider exists"
else
  aws iam create-open-id-connect-provider \
    --url "https://${GITHUB_OIDC_HOST}" \
    --client-id-list "$GITHUB_OIDC_AUDIENCE" \
    --thumbprint-list "$GITHUB_OIDC_THUMBPRINT" \
    --no-cli-pager >/dev/null
  log "  OIDC provider created"
fi
cat > "$WORKDIR/deploy-trust.json" <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Federated": "${OIDC_ARN}" },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${GITHUB_OIDC_HOST}:aud": "${GITHUB_OIDC_AUDIENCE}",
          "${GITHUB_OIDC_HOST}:sub": "repo:${GITHUB_REPO}:ref:${GITHUB_DEPLOY_REF}"
        }
      }
    }
  ]
}
JSON
if aws iam get-role --role-name "$GITHUB_DEPLOY_ROLE" --no-cli-pager >/dev/null 2>&1; then
  aws iam update-assume-role-policy --role-name "$GITHUB_DEPLOY_ROLE" \
    --policy-document "file://$WORKDIR/deploy-trust.json" --no-cli-pager
  log "  deploy role exists (trust refreshed)"
else
  aws iam create-role --role-name "$GITHUB_DEPLOY_ROLE" \
    --assume-role-policy-document "file://$WORKDIR/deploy-trust.json" \
    --description "washa GitHub Actions deploy role (OIDC)" --no-cli-pager >/dev/null
  aws iam wait role-exists --role-name "$GITHUB_DEPLOY_ROLE"
  log "  deploy role created"
fi
cat > "$WORKDIR/deploy-policy.json" <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "LambdaCodeDeploy",
      "Effect": "Allow",
      "Action": [
        "lambda:UpdateFunctionCode",
        "lambda:GetFunction",
        "lambda:GetFunctionConfiguration",
        "lambda:UpdateFunctionConfiguration"
      ],
      "Resource": "${LAMBDA_ARN}"
    },
    {
      "Sid": "SsmReadParams",
      "Effect": "Allow",
      "Action": ["ssm:GetParameter", "ssm:GetParameters"],
      "Resource": "arn:aws:ssm:${AWS_REGION}:${ACCOUNT_ID}:parameter${SSM_PREFIX}/*"
    },
    {
      "Sid": "KmsDecryptViaSsm",
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "*",
      "Condition": { "StringEquals": { "kms:ViaService": "ssm.${AWS_REGION}.amazonaws.com" } }
    },
    {
      "Sid": "CloudFrontInvalidate",
      "Effect": "Allow",
      "Action": ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"],
      "Resource": "${DIST_ARN}"
    }
  ]
}
JSON
aws iam put-role-policy --role-name "$GITHUB_DEPLOY_ROLE" \
  --policy-name "$GITHUB_DEPLOY_POLICY" \
  --policy-document "file://$WORKDIR/deploy-policy.json" --no-cli-pager
DEPLOY_ROLE_ARN="$(aws iam get-role --role-name "$GITHUB_DEPLOY_ROLE" --query Role.Arn --output text --no-cli-pager)"
log "  least-privilege policy attached"

# ── 12/12 · SSM SecureString slots (created only if absent; never clobber seeded values) ─────────
log "12/12 SSM SecureString slots under ${SSM_PREFIX}"
for i in "${!SSM_PARAM_PATHS[@]}"; do
  path="${SSM_PARAM_PATHS[$i]}"
  if aws ssm get-parameter --name "$path" --no-cli-pager >/dev/null 2>&1; then
    log "  exists ${path}"
  else
    aws ssm put-parameter --name "$path" --type "${PARAM_TYPES[$i]}" --value "REPLACE_ME" \
      --description "washa runtime config slot — populate via seed-secrets.sh" --no-cli-pager >/dev/null
    log "  created slot ${path} (${PARAM_TYPES[$i]})"
  fi
done

# ── Outputs ──────────────────────────────────────────────────────────────────────────────────────
cat <<EOF

────────────────────────────────────────────────────────────────────────────
washa stack provisioned. Set these as GitHub repository variables:

  AWS_DEPLOY_ROLE_ARN        ${DEPLOY_ROLE_ARN}
  CLOUDFRONT_DISTRIBUTION_ID ${DIST_ID}

  CloudFront domain : ${DIST_DOMAIN}
  Public URL        : https://${DOMAIN}
  Function URL      : ${FUNCTION_URL}

Next:
  1. bash infra/cli/seed-secrets.sh .env.prod   # push the 10 prod values into SSM
  2. bash infra/cli/set-lambda-env.sh           # materialize them onto the Lambda
  3. Set the two GitHub repository variables above.
  4. Add the Google OAuth redirect URI:
       https://${DOMAIN}/sso/sign-in/oidc/callback/google
  5. Run the CD workflow to ship the first native binary.

CloudFront can take ~15 minutes to finish deploying; DNS may take longer to propagate.
────────────────────────────────────────────────────────────────────────────
EOF
