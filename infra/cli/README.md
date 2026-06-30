# washa infrastructure — `aws` CLI variant

Bash + the `aws` CLI scripts that stand up washa's full production stack. This is one of two
interchangeable provisioners: it creates the **same resources, names, and settings** as
[`../terraform/`](../terraform/). Pick one — do not run both against the same account. For the
big-picture architecture, the secret flow, and the cost notes, see [`../README.md`](../README.md);
this file documents the scripts themselves.

The scripts cover one-time **infrastructure creation** plus secret handling. Ongoing **code** deploys
(shipping a new native binary) stay with `.github/workflows/cd.yml`, which targets the `oppshan-washa` Lambda
and the CloudFront distribution this stack creates.

## Prerequisites

- **AWS CLI v2**, configured with credentials that can create IAM, Lambda, CloudFront, ACM, Route 53,
  SSM, and CloudWatch Logs resources (`aws configure` or an SSO profile).
- **`jq`** and **`zip`** on `PATH`.
- The Route 53 hosted zone for **`oppshan.com`** must already exist in the same account (it is looked
  up, never created).
- Region split is automatic: everything goes to **`ap-southeast-1`** except the ACM certificate,
  which the scripts create in **`us-east-1`** (a CloudFront requirement).

## Scripts

| Script | What it does |
|---|---|
| `lib.sh` | Sourced by the others. Defines the canonical config (names, regions, sizing, the runtime-config → SSM map, the managed CloudFront policy IDs) and the `log` / `die` / `require_cmds` / `account_id` helpers. Not run directly. |
| `provision.sh` | Stands up the entire stack in dependency order: Lambda exec role → log group → `oppshan-washa` Lambda (placeholder zip) → Function URL → OAC → ACM cert → Route 53 validation + wait → CloudFront → Lambda permission → Route 53 alias → GitHub OIDC provider + deploy role → 7 SSM slots. **Idempotent** — re-run it after a failure; it creates only what is missing and never clobbers seeded secrets or deployed code. Prints the two GitHub variable values at the end. |
| `seed-secrets.sh` | Pushes the 7 runtime values into SSM as `SecureString`. Reads them from the values file you pass (e.g. `.env.prod` for prod), defaulting to the repo-root `.env`; read-only, and if the file is absent it prompts (secrets read silently). Never echoes secret values. Shared with the Terraform path. |
| `set-lambda-env.sh` | Reads the 7 SSM values (decrypted) and applies them as the Lambda's environment under their `QUARKUS_*` / `OPPSHAN_WASHA_ALLOWED_IDENTITIES` names. Never echoes secret values. Shared with the Terraform path. |
| `destroy.sh` | Reverse-order teardown. Destructive: prompts for `yes` (or pass `--force`). Best-effort, so a partial teardown re-runs cleanly. Leaves the `oppshan.com` hosted zone intact. |

## Run order

```bash
bash infra/cli/provision.sh                 # 1. create the stack (note the two printed values)
bash infra/cli/seed-secrets.sh .env.prod    # 2. push the 7 prod values into SSM
bash infra/cli/set-lambda-env.sh            # 3. materialize them onto the Lambda
```

4. Set the two **GitHub repository variables** (Settings → Secrets and variables → Actions →
   Variables) from `provision.sh`'s output:
   - `AWS_DEPLOY_ROLE_ARN` — the `oppshan-washa-github-deploy` role ARN
   - `CLOUDFRONT_DISTRIBUTION_ID` — the distribution ID
5. Add the **Google OAuth redirect URI** in the Google Cloud console:
   `https://washa.oppshan.com/sso/sign-in/oidc/callback/google`
6. Run the **`CD`** workflow (or `aws lambda update-function-code`) to ship the first native binary,
   replacing the placeholder.

CloudFront can take ~15 minutes to finish deploying, and DNS may take longer to propagate, so the
site will not answer on `https://washa.oppshan.com` the instant `provision.sh` returns.

## Teardown

```bash
bash infra/cli/destroy.sh          # prompts for confirmation
bash infra/cli/destroy.sh --force  # no prompt (e.g. scripted cleanup)
```

This deletes the secret values in SSM along with everything else. Re-seed with `seed-secrets.sh .env.prod`
after a fresh `provision.sh` if you stand the stack back up.
