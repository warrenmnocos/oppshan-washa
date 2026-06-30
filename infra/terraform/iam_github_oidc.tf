variable "create_github_oidc_provider" {
  description = "Create the account-global GitHub OIDC provider, or reference the existing one. In this AWS account a sibling app already created it, so the default is false (Terraform references it). Set true only when provisioning into a fresh account that has no GitHub OIDC provider."
  type        = bool
  default     = false
}

data "aws_caller_identity" "current" {}

# GitHub Actions OIDC: cd.yml assumes a scoped role with NO long-lived AWS key. The provider is
# account-global, so it is created at most once per account (toggle above).
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # AWS no longer verifies this thumbprint for the well-known GitHub IdP, but the attribute is still
  # required. Both of GitHub's published values are listed.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

locals {
  github_oidc_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

# Trust policy: only GitHub Actions runs for this repo + ref may assume the role.
data "aws_iam_policy_document" "github_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:${var.github_deploy_ref}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${var.function_name}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

# Least-privilege deploy policy: ship code, materialize env from SSM, invalidate the edge cache.
data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid    = "DeployFunctionCodeAndConfig"
    effect = "Allow"
    actions = [
      "lambda:UpdateFunctionCode",
      "lambda:GetFunction",
      "lambda:GetFunctionConfiguration",
      "lambda:UpdateFunctionConfiguration",
    ]
    resources = [aws_lambda_function.washa.arn]
  }

  statement {
    sid       = "ReadRuntimeConfig"
    effect    = "Allow"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = ["arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_prefix}/*"]
  }

  # Decrypt SecureStrings — constrained to decrypts mediated by SSM in this region (covers the
  # AWS-managed alias/aws/ssm key without hardcoding its key id, and survives a later switch to a CMK).
  statement {
    sid       = "DecryptViaSsm"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.aws_region}.amazonaws.com"]
    }
  }

  statement {
    sid       = "InvalidateEdgeCache"
    effect    = "Allow"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [aws_cloudfront_distribution.washa.arn]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
