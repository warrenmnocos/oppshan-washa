# Deployment infrastructure

Two interchangeable ways to provision washa's production stack on AWS. Pick **one** — they create the
same resources with the same names. Step-by-step guides live in [`../docs/`](../docs/): the
[console manual](../docs/aws-deployment-manual.md), [CLI](../docs/aws-deployment-cli.md),
[Terraform](../docs/aws-deployment-terraform.md), and [recovery](../docs/aws-deployment-recovery.md);
this file is the quick reference.

- **`terraform/`** — declarative IaC (recommended).
- **`cli/`** — `aws` CLI bash scripts that stand up the identical stack, plus the shared secret
  scripts (`seed-secrets.sh`, `set-lambda-env.sh`) that the Terraform path also uses.

Ongoing application deploys (shipping a new native binary) are handled separately by
`.github/workflows/cd.yml`; this directory only creates the infrastructure that workflow targets.

## What gets created

```
Route 53 (washa.oppshan.com — existing zone)
  → CloudFront (ACM TLS in us-east-1, OAC sigv4, edge cache)
      → Lambda Function URL (AWS_IAM — only OAC-signed CloudFront can reach it)
          → washa Lambda (GraalVM-native arm64, provided.al2023)
              → Neon PostgreSQL (external)
```

Everything is in `ap-southeast-1` (Singapore, co-located with Neon) except the ACM certificate, which CloudFront requires in
`us-east-1`. The Route 53 hosted zone for `oppshan.com` already exists and is reused, not created.

## Secrets

Secrets never live in this repo, in Terraform state, or in a `.tfvars` file. They flow:

```
your gitignored .env  ──seed-secrets.sh──▶  SSM Parameter Store        ──set-lambda-env.sh──▶  Lambda
                                            (SecureString, KMS-encrypted)                       environment
```

Terraform declares each SSM parameter as an empty slot (SecureString, or String for the non-secret
datasource URL and username) and ignores its value, and declares the Lambda with its environment
ignored. So neither the secret values nor the Lambda environment are managed by Terraform — both are
populated out-of-band by the two scripts in `cli/`, which both provisioning paths use.

The seven values (names and types mirror oppshan-files' `/oppshan/*` convention; the env var name
equals the SSM suffix):

| Lambda env var | SSM parameter | Type |
|---|---|---|
| `GOOGLE_CLIENT_ID` | `/oppshan/washa/GOOGLE_CLIENT_ID` | SecureString |
| `GOOGLE_CLIENT_SECRET` | `/oppshan/washa/GOOGLE_CLIENT_SECRET` | SecureString |
| `TOKEN_ENCRYPTION_SECRET` | `/oppshan/washa/TOKEN_ENCRYPTION_SECRET` | SecureString |
| `QUARKUS_DATASOURCE_JDBC_URL` | `/oppshan/washa/QUARKUS_DATASOURCE_JDBC_URL` | String |
| `QUARKUS_DATASOURCE_USERNAME` | `/oppshan/washa/QUARKUS_DATASOURCE_USERNAME` | String |
| `QUARKUS_DATASOURCE_PASSWORD` | `/oppshan/washa/QUARKUS_DATASOURCE_PASSWORD` | SecureString |
| `WASHA_ALLOWED_IDENTITIES` | `/oppshan/washa/WASHA_ALLOWED_IDENTITIES` | SecureString |

## Runbook

1. **Provision** — `cd terraform && terraform init && terraform apply`  *(or)*  `bash cli/provision.sh`
2. **Seed secrets** — `bash cli/seed-secrets.sh`
3. **Materialize env** — `bash cli/set-lambda-env.sh`
4. **GitHub repo variables** (from the provision outputs) — `AWS_DEPLOY_ROLE_ARN`, `CLOUDFRONT_DISTRIBUTION_ID`
5. **Google OAuth** — add redirect URI `https://washa.oppshan.com/sso/sign-in/oidc/callback/google`
6. **Ship the binary** — run the `CD` workflow (or `aws lambda update-function-code`)

## Cost

Everything sits inside AWS always-free tiers (Lambda, CloudFront, ACM) plus the existing
~$0.50/month Route 53 zone shared across the domain. Compute and database both scale to zero when
idle, so the steady-state cost is effectively nothing.
