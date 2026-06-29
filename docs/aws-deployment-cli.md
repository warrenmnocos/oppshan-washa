# AWS Deployment Guide — CLI-Based (No Terraform)

Deploy `washa` with the `aws` CLI, driven by the scripts in [`infra/cli/`](../infra/cli/). Same architecture as the [manual](aws-deployment-manual.md) and [Terraform](aws-deployment-terraform.md) guides; pick one path. The scripts stand up the entire stack and are **idempotent** — a partial or interrupted run can simply be re-run.

## What stays manual vs automated

| Manual (you do once) | Automated (the scripts do) |
|---|---|
| Provision the Neon database (Phase 0); have its pooled JDBC URL / user / password | Lambda + exec role + log group, Function URL, OAC, CloudFront, ACM + DNS validation, Route 53 alias, SSM parameter slots, GitHub deploy role |
| Create the Google OAuth client; add the redirect URI (Phase 4) | — |
| Set the two GitHub repo variables from the script output (Phase 3) | — |

## Prerequisites
- `aws` CLI v2, configured with `oppshan-admin` credentials and region **ap-southeast-1** (`aws configure get region` → `ap-southeast-1`).
- `jq` and `zip` on PATH.
- Shared, already-provisioned (confirm, see the manual Phase 1): the AWS account, the `oppshan.com` Route 53 zone, and the GitHub OIDC provider. `provision.sh` detects the existing OIDC provider and reuses it.

## Phase 0: Provision the Neon database (neonctl)

washa's data lives in **Neon** (serverless Postgres, external to AWS). If the `oppshan` Neon database already exists, skip to the last command and just grab its connection string. Otherwise:
```bash
npm i -g neonctl                       # or: brew install neonctl
neonctl auth                           # opens a browser to authenticate

# Create the project in Singapore, co-located with the ap-southeast-1 Lambda (keeps the DB-heavy /compute path in-region):
neonctl projects create --name oppshan --region-id aws-ap-southeast-1   # org-level project; washa is a schema in its oppshan database
neonctl link --project-id <PROJECT_ID>   # from the output, so you can omit --project-id below

# The app database (Flyway creates the `washa` schema inside it) + a role:
neonctl databases create --name oppshan
neonctl roles create --name washa   # created on the project's default branch

# Pooled connection string (the Lambda uses Neon's pgbouncer endpoint):
neonctl connection-string --database-name oppshan --role-name washa --pooled
#   -> postgresql://washa:<password>@<host>-pooler.ap-southeast-1.aws.neon.tech/oppshan?sslmode=require
```
Scale-to-zero (5-minute autosuspend) is mandatory on the Free plan — no flag needed, and it's what keeps Neon at $0 when idle. Put the derived values in the gitignored repo-root `.env` (or enter them when `seed-secrets.sh` prompts):
```
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://<host>-pooler.ap-southeast-1.aws.neon.tech/oppshan?sslmode=require
QUARKUS_DATASOURCE_USERNAME=washa
QUARKUS_DATASOURCE_PASSWORD=<password>
```

## Phase 1: Provision the stack
```bash
bash infra/cli/provision.sh
```
Creates, in dependency order: the `oppshan-washa-lambda-exec` role + `/aws/lambda/oppshan-washa` log group → the `oppshan-washa` Lambda (placeholder binary, 256 MB, arm64, reserved concurrency 5) → the `AWS_IAM` Function URL → the `oppshan-washa-oac` Origin Access Control → the **us-east-1** ACM certificate (it writes the validation record into the `oppshan.com` zone and waits for issuance) → the CloudFront distribution (three behaviors, the `X-Forwarded-Host`/`-Proto` origin headers) → the `lambda:InvokeFunctionUrl` permission scoped to the distribution → the `washa.oppshan.com` A/AAAA aliases → the `oppshan-washa-github-deploy` role + least-privilege policy → the seven `/oppshan/washa/*` SSM slots (value `REPLACE_ME`).

It prints the two values you need next:
```
AWS_DEPLOY_ROLE_ARN        arn:aws:iam::<acct>:role/oppshan-washa-github-deploy
CLOUDFRONT_DISTRIBUTION_ID E………
```

## Phase 2: Seed the secrets, materialize the env
```bash
bash infra/cli/seed-secrets.sh      # reads the gitignored repo-root .env (or prompts), writes SSM SecureStrings
bash infra/cli/set-lambda-env.sh    # reads SSM (decrypted) and sets the Lambda's environment
```
`seed-secrets.sh` reads the same `.env` keys dev uses (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `TOKEN_ENCRYPTION_SECRET`, `QUARKUS_DATASOURCE_{JDBC_URL,USERNAME,PASSWORD}`, `OPPSHAN_WASHA_ALLOWED_IDENTITIES`); if there's no `.env` it prompts (secrets silently). Neither script ever echoes a secret value. No secret is written to disk or to any committed file — only into encrypted SSM and the IAM-protected Lambda config.

## Phase 3: GitHub repo variables
In **GitHub → Settings → Secrets and variables → Actions → Variables**, set `AWS_DEPLOY_ROLE_ARN` and `CLOUDFRONT_DISTRIBUTION_ID` to the values `provision.sh` printed. `cd.yml` is gated on these.

## Phase 4: Google OAuth redirect URI
Add `https://washa.oppshan.com/sso/sign-in/oidc/callback/google` to the OAuth client's authorized redirect URIs.

## Phase 5: Ship the binary + verify
Run the **CD** workflow (it builds the native arm64 artifact, `aws lambda update-function-code`s `oppshan-washa`, and invalidates the cache), or by hand:
```bash
aws lambda update-function-code --function-name oppshan-washa --zip-file fileb://target/function.zip --region ap-southeast-1
aws lambda wait function-updated --function-name oppshan-washa --region ap-southeast-1
```
Wait for the distribution to reach **Deployed**, then visit `https://washa.oppshan.com`. Smoke checks are in the [manual](aws-deployment-manual.md#smoke-checks); failures are in [recovery](aws-deployment-recovery.md).

## Teardown
```bash
bash infra/cli/destroy.sh           # guarded — type "yes", or pass --force
```
Reverse-order, best-effort teardown. The `oppshan.com` zone is never deleted (it's only looked up), and the GitHub OIDC provider it shares with `oppshan-files` is deleted only if `provision.sh` had created it. Neon is external — destroy there separately.

## How the scripts fit together
All four source [`infra/cli/lib.sh`](../infra/cli/lib.sh), the single place that holds the canonical names, region, sizing, the SSM map, and the managed CloudFront policy IDs — mirroring [`infra/terraform/variables.tf`](../infra/terraform/variables.tf). `seed-secrets.sh` and `set-lambda-env.sh` are shared with the Terraform path (run them after `terraform apply` too).
