# AWS Deployment Recovery Scenarios

How to diagnose and fix a broken `washa` deployment. Unlike a VM-based stack, there is **no instance to terminate and rebuild** — washa is serverless, so almost every recovery is a re-run of a script, a workflow, or a single `aws` call. The durable data lives in **Neon**, which none of these steps touch, so they are safe to repeat.

## The universal pattern
1. **Look at the logs** — `/aws/lambda/washa` in CloudWatch tells you most things.
2. **Re-apply the layer that's wrong** — code (`update-function-code`), config (`set-lambda-env.sh`), edge (CloudFront invalidation), or DNS/cert (Route 53 / ACM).
3. **Invalidate CloudFront** if the fix is code/asset-related.

## Diagnostic recipes
```bash
# Tail the function logs (most failures show here)
aws logs tail /aws/lambda/washa --follow --region ap-northeast-1

# What env vars does the function actually have? (names only — values are not printed safely)
aws lambda get-function-configuration --function-name washa --region ap-northeast-1 \
  --query 'Environment.Variables | keys(@)'

# Is the distribution finished deploying? what cert + aliases is it using?
aws cloudfront get-distribution --id <DIST_ID> --query 'Distribution.{Status:Status,Aliases:DistributionConfig.Aliases,Cert:DistributionConfig.ViewerCertificate}'

# Cert issued? validation record present?
aws acm describe-certificate --region us-east-1 --certificate-arn <CERT_ARN> --query 'Certificate.Status'
```

## Scenario 1 — Bad deploy: 5xx or wrong behavior after shipping
Roll the code back to the previous artifact (download the prior `CD` run's `washa-lambda` artifact, or rebuild from the previous commit), then:
```bash
aws lambda update-function-code --function-name washa --zip-file fileb://function.zip --region ap-northeast-1
aws lambda wait function-updated --function-name washa --region ap-northeast-1
aws cloudfront create-invalidation --distribution-id <DIST_ID> --paths '/*'
```

## Scenario 2 — Function won't start / init errors
Check CloudWatch first. Common causes:
- **A missing or empty env var** — re-run `bash infra/cli/set-lambda-env.sh` (and confirm the SSM slot isn't still `REPLACE_ME`: `bash infra/cli/seed-secrets.sh` first).
- **Neon unreachable** — verify `QUARKUS_DATASOURCE_JDBC_URL` (host, `?sslmode=require`), the username/password, and that Neon isn't mid cold-start (the first request after autosuspend is slow, not failed). The Lambda timeout is 30 s.
- **Schema drift** — Hibernate validates the mapped entities against the Flyway-built schema on boot and halts on mismatch; the log names the offending column. Fix the migration, redeploy.

## Scenario 3 — Sign-in broken / redirect loops
- **Redirect goes to the Function URL host, not `washa.oppshan.com`** — the CloudFront origin is missing the `X-Forwarded-Host` / `X-Forwarded-Proto` custom headers (the app needs them because OAC drops the viewer Host). Re-add them on the origin.
- **Google "redirect_uri_mismatch"** — the OAuth client is missing `https://washa.oppshan.com/sso/sign-in/oidc/callback/google`.
- **"Access denied" after a successful Google login** — the email isn't in `WASHA_ALLOWED_IDENTITIES`. Update the SSM value and re-run `set-lambda-env.sh` (see Scenario 7).

## Scenario 4 — 403 from the app / CloudFront can't reach the origin
The Function URL is `AWS_IAM`-auth, so only OAC-signed CloudFront may call it. Check:
- The Lambda resource-based policy has `AllowCloudFrontInvokeFunctionUrl` (`lambda:InvokeFunctionUrl`, principal `cloudfront.amazonaws.com`, `SourceArn` = the distribution ARN).
- The distribution's origin uses the `washa-oac` Origin Access Control (signing **always**, **sigv4**, origin type **lambda**).

## Scenario 5 — Stale SPA shell or assets after a deploy
CloudFront cached the old `index.html`/assets. Invalidate: `aws cloudfront create-invalidation --distribution-id <DIST_ID> --paths '/*'` (the `CD` workflow does this automatically). The default behavior caches per the origin's `Cache-Control`; `/api/*` and `/sso/*` are never cached.

## Scenario 6 — TLS / certificate errors
- The certificate **must** be in **us-east-1** and cover `washa.oppshan.com`; the distribution must reference that ARN with `sni-only` + `TLSv1.2_2021`.
- DNS-validated ACM certs **auto-renew** as long as the validation CNAME stays in the `oppshan.com` zone — don't delete it.

## Scenario 7 — Rotate a secret
```bash
# Put the new value (edit .env, or it prompts), then push it onto the function:
bash infra/cli/seed-secrets.sh
bash infra/cli/set-lambda-env.sh
```
The new env takes effect on the next cold start; force it by redeploying the code (Scenario 1) if needed.

## Scenario 8 — CI deploy fails to assume the role
- The OIDC `sub` must match the trust policy: `repo:warrenmnocos/oppshan-washa:ref:refs/heads/main` (deploys run from `main`).
- The repo variable `AWS_DEPLOY_ROLE_ARN` must be set (and `CLOUDFRONT_DISTRIBUTION_ID` for the invalidation step).
- The GitHub OIDC provider must exist in the account (it's shared with `oppshan-files`).

## Scenario 9 — Start over cleanly
`bash infra/cli/destroy.sh --force` (or `terraform destroy`), then re-provision. Neon holds the data and is never touched, so a full infra rebuild loses nothing. Re-seed secrets and re-set the env afterward.
