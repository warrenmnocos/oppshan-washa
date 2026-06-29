# Terraform — washa AWS stack

Declarative provisioning of the production stack. See [../README.md](../README.md) for the topology,
the secrets model, and the post-apply runbook shared by both provisioning paths.

## Usage

```bash
terraform init
terraform plan
terraform apply
```

ACM DNS validation completes automatically through the existing `oppshan.com` Route 53 zone, so
`apply` runs unattended.

## State

State is **local** and gitignored. It holds no secrets — the SSM parameter values and the Lambda
environment are managed out-of-band — so local state is safe here. To move to encrypted remote state,
uncomment the S3 block in `backend.tf`, create the bucket and lock table, then run
`terraform init -migrate-state`.

## Notes

- The GitHub Actions OIDC provider is account-global. If another app in the same AWS account already
  created it, set `create_github_oidc_provider = false` and Terraform will reference the existing one.
- The Lambda is created with a placeholder binary and `ignore_changes` on its code and environment,
  so applies never fight the deployed binary or the out-of-band env vars.
- Every input has a sensible default in `variables.tf`; `terraform.tfvars.example` documents the knobs.
