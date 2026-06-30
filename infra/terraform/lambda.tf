# Placeholder package so the function can be created before any native artifact exists. The real
# arm64 binary arrives via `aws lambda update-function-code` (cd.yml / infra/cli); ignore_changes
# below keeps Terraform from reverting it.
data "archive_file" "placeholder" {
  type        = "zip"
  output_path = "${path.module}/.terraform/placeholder.zip"

  source {
    content  = file("${path.module}/placeholder/bootstrap")
    filename = "bootstrap"
  }
}

# --- Execution role: CloudWatch Logs only. No VPC (Neon is reached over public TLS); no runtime SSM
#     (the app reads env vars, which are materialized out-of-band). ---
data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_exec" {
  name               = "${var.function_name}-lambda-exec"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.function_name}"
  retention_in_days = var.log_retention_days
}

data "aws_iam_policy_document" "lambda_logs" {
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.lambda.arn}:*"]
  }
}

resource "aws_iam_role_policy" "lambda_logs" {
  name   = "logs"
  role   = aws_iam_role.lambda_exec.id
  policy = data.aws_iam_policy_document.lambda_logs.json
}

# --- The function: GraalVM-native arm64 binary on the provided.al2023 custom runtime. ---
resource "aws_lambda_function" "washa" {
  function_name = var.function_name
  role          = aws_iam_role.lambda_exec.arn

  runtime       = "provided.al2023"
  handler       = "not.used.in.provided.runtime"
  architectures = ["arm64"]

  filename         = data.archive_file.placeholder.output_path
  source_code_hash = data.archive_file.placeholder.output_base64sha256

  memory_size                    = var.lambda_memory_mb
  timeout                        = var.lambda_timeout_s
  reserved_concurrent_executions = var.lambda_reserved_concurrency

  depends_on = [aws_iam_role_policy.lambda_logs, aws_cloudwatch_log_group.lambda]

  lifecycle {
    # The deployed binary and the out-of-band env vars are owned by the deploy pipeline, not by
    # Terraform. Without this, every apply would revert the function to the placeholder + empty env.
    ignore_changes = [filename, source_code_hash, environment]
  }
}

# Function URL — reachable ONLY by OAC-signed CloudFront (IAM auth). No CORS: it is same-origin
# behind CloudFront.
resource "aws_lambda_function_url" "washa" {
  function_name      = aws_lambda_function.washa.function_name
  authorization_type = "AWS_IAM"
}

# Allow this CloudFront distribution (and only it) to invoke the Function URL via OAC. OAC→Lambda
# needs BOTH grants: InvokeFunctionUrl authorizes the Function URL's IAM auth, and InvokeFunction
# authorizes the invocation itself. With only the first, CloudFront's OAC-signed request gets a 403
# (x-amzn-errortype: AccessDeniedException) and the function is never invoked (zero CloudWatch
# invocations). See docs/aws-deployment-recovery.md Scenario 4.
resource "aws_lambda_permission" "cloudfront_invoke_url" {
  statement_id           = "AllowCloudFrontInvokeFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.washa.function_name
  principal              = "cloudfront.amazonaws.com"
  source_arn             = aws_cloudfront_distribution.washa.arn
  function_url_auth_type = "AWS_IAM"
}

resource "aws_lambda_permission" "cloudfront_invoke_function" {
  statement_id  = "AllowCloudFrontInvokeFunction"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.washa.function_name
  principal     = "cloudfront.amazonaws.com"
  source_arn    = aws_cloudfront_distribution.washa.arn
}
