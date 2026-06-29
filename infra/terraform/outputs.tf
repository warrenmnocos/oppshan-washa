output "deploy_role_arn" {
  description = "Set as the GitHub repo variable AWS_DEPLOY_ROLE_ARN (cd.yml assumes it)."
  value       = aws_iam_role.github_deploy.arn
}

output "cloudfront_distribution_id" {
  description = "Set as the GitHub repo variable CLOUDFRONT_DISTRIBUTION_ID (cd.yml invalidates it)."
  value       = aws_cloudfront_distribution.washa.id
}

output "cloudfront_domain_name" {
  description = "The distribution's *.cloudfront.net domain (the Route 53 alias targets this)."
  value       = aws_cloudfront_distribution.washa.domain_name
}

output "lambda_function_url" {
  description = "The Function URL — private; only OAC-signed CloudFront may call it."
  value       = aws_lambda_function_url.washa.function_url
}

output "public_url" {
  description = "The public application URL."
  value       = "https://${var.domain_name}"
}

output "ssm_parameter_names" {
  description = "The SSM parameters to seed with ../cli/seed-secrets.sh."
  value       = sort([for p in aws_ssm_parameter.washa : p.name])
}

output "next_steps" {
  description = "Post-apply runbook."
  value       = <<-EOT
    1. Seed secrets:    bash ../cli/seed-secrets.sh
    2. Materialize env: bash ../cli/set-lambda-env.sh
    3. GitHub repo vars:
         AWS_DEPLOY_ROLE_ARN        = ${aws_iam_role.github_deploy.arn}
         CLOUDFRONT_DISTRIBUTION_ID = ${aws_cloudfront_distribution.washa.id}
    4. Google OAuth redirect URI: https://${var.domain_name}/sso/sign-in/oidc/callback/google
    5. Ship the binary: run the CD workflow (or aws lambda update-function-code).
  EOT
}
