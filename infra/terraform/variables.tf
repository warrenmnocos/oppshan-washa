variable "aws_region" {
  description = "Region for the Lambda, CloudFront origin, SSM, and IAM (Singapore, co-located with Neon)."
  type        = string
  default     = "ap-southeast-1"
}

variable "domain_name" {
  description = "Public hostname served by CloudFront."
  type        = string
  default     = "washa.oppshan.com"
}

variable "hosted_zone_name" {
  description = "Existing Route 53 hosted zone that owns the domain. Looked up, never created."
  type        = string
  default     = "oppshan.com"
}

variable "function_name" {
  description = "Lambda function name. cd.yml targets this literal name — keep the two in sync."
  type        = string
  default     = "oppshan-washa"
}

variable "lambda_memory_mb" {
  description = "Lambda memory (MB). CPU scales with this; 256 is ample for the native binary."
  type        = number
  default     = 256
}

variable "lambda_timeout_s" {
  description = "Lambda timeout (seconds). Should be >= the CloudFront origin read timeout."
  type        = number
  default     = 30
}

variable "lambda_reserved_concurrency" {
  description = "Reserved concurrency (caps cost, protects Neon's small pool). -1 = unreserved (the default): a new AWS account caps total concurrency at 10, which leaves no room to reserve any (AWS keeps the unreserved pool >= 10), so a reservation fails until you raise the Lambda concurrency quota. Set 5 once the quota allows."
  type        = number
  default     = -1
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for the function."
  type        = number
  default     = 14
}

variable "ssm_prefix" {
  description = "SSM parameter path prefix. Nests under the shared /oppshan org tree (oppshan-files sits at /oppshan directly)."
  type        = string
  default     = "/oppshan/washa"
}

variable "github_repo" {
  description = "owner/name of the GitHub repository allowed to assume the deploy role via OIDC."
  type        = string
  default     = "warrenmnocos/oppshan-washa"
}

variable "github_deploy_ref" {
  description = "Git ref whose GitHub Actions runs may assume the deploy role (the OIDC subject claim)."
  type        = string
  default     = "refs/heads/main"
}

variable "price_class" {
  description = "CloudFront price class. PriceClass_200 includes the Tokyo edge and is the cost sweet spot."
  type        = string
  default     = "PriceClass_200"
}
