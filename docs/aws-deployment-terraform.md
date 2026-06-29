# AWS Deployment Guide — Terraform

Declarative provisioning of `washa` from [`infra/terraform/`](../infra/terraform/). Same architecture as the [manual](aws-deployment-manual.md) and [CLI](aws-deployment-cli.md) guides; pick one path. Unlike `oppshan-files` (whose Terraform is inlined in its docs), washa keeps **runnable** `.tf` files — this guide walks through them rather than repeating them.

## What Terraform automates
The `washa` Lambda + execution role + log group, the `AWS_IAM` Function URL + the CloudFront invoke permission, the `washa-oac` Origin Access Control, the CloudFront distribution (three behaviors + the forwarded-host headers), the **us-east-1** ACM certificate + its DNS validation, the `washa.oppshan.com` Route 53 aliases, the seven `/oppshan/washa/*` SSM parameter slots, and the `washa-github-deploy` role.

## What stays manual (Terraform doesn't touch)
- The **Neon** database (external managed service) — have its JDBC URL / user / password ready.
- The **Google OAuth** client and its redirect URI.
- The **secret values** — seeded into SSM out-of-band (see "Secrets" below), never in Terraform state.
- The two **GitHub repo variables** (set from the outputs).

## Prerequisites
- Terraform ≥ 1.6 and the `aws` CLI configured with `oppshan-admin` credentials.
- The shared prerequisites (account, `oppshan.com` zone, GitHub OIDC provider) — see the manual Phase 1. Because the OIDC provider already exists in this account, `create_github_oidc_provider` defaults to **false** (Terraform references it). Set it `true` only for a fresh account.

## File layout
```
infra/terraform/
├── versions.tf        # terraform >=1.6; aws ~>5.0; archive
├── providers.tf       # aws (ap-northeast-1) + aliased aws.use1 (us-east-1, for ACM)
├── backend.tf         # local state (commented S3 block + migrate note)
├── variables.tf       # domain, zone, region, sizing, github repo, ssm prefix
├── terraform.tfvars.example
├── ssm.tf             # 7 SecureString/String slots (lifecycle ignores value)
├── lambda.tf          # function (placeholder zip), URL, permission, exec role, log group
├── acm.tf             # cert in us-east-1 + DNS validation
├── route53.tf         # data zone lookup + validation records + A/AAAA alias
├── cloudfront.tf      # OAC, managed-policy data sources, distribution
├── iam_github_oidc.tf # OIDC provider (create-or-reference) + deploy role + policy
└── outputs.tf         # role ARN, distribution id, function URL, gh-var hints
```

## Workflow
```bash
cd infra/terraform
terraform init
terraform plan      # ~22 resources to add against a clean account
terraform apply
```
ACM DNS validation completes automatically through the existing `oppshan.com` zone, so `apply` runs unattended. Defaults in `variables.tf` are correct for this project; `terraform.tfvars.example` documents the knobs (region, sizing, `create_github_oidc_provider`, …).

## Secrets — why state holds none
Terraform declares each SSM parameter as a placeholder slot (`value = "REPLACE_ME"`, `lifecycle { ignore_changes = [value] }`) and declares the Lambda with `ignore_changes = [environment, source_code_hash, filename]`. So Terraform never reads or writes a real secret, and never fights the deployed binary or the out-of-band env. After `apply`, materialize the real values with the shared CLI scripts:
```bash
bash ../cli/seed-secrets.sh      # .env/prompt → SSM
bash ../cli/set-lambda-env.sh    # SSM → Lambda environment
```
State is therefore secret-free, so it lives **locally** (gitignored). To move to encrypted remote state, uncomment the S3 block in `backend.tf` and run `terraform init -migrate-state`.

## After apply
1. Read the outputs: `terraform output` (deploy role ARN, distribution id, function URL, `next_steps`).
2. Set the GitHub repo variables `AWS_DEPLOY_ROLE_ARN` and `CLOUDFRONT_DISTRIBUTION_ID`.
3. Add the Google OAuth redirect URI `https://washa.oppshan.com/sso/sign-in/oidc/callback/google`.
4. Run the **CD** workflow to ship the first native binary, then verify `https://washa.oppshan.com`.

## Teardown
```bash
terraform destroy
```
Removes everything Terraform created. The `oppshan.com` zone (a data source) and the shared GitHub OIDC provider (referenced, not created, under the default toggle) are left intact. Neon is external — destroy there separately. Recovery scenarios are in [`aws-deployment-recovery.md`](aws-deployment-recovery.md).
