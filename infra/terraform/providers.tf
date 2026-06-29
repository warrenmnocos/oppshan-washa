# Everything lives in Singapore (ap-southeast-1), co-located with Neon.
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "washa"
      ManagedBy = "terraform"
    }
  }
}

# CloudFront requires its ACM certificate in us-east-1, so a second aliased provider exists purely to
# create and validate that one certificate. Reference it with `provider = aws.use1`.
provider "aws" {
  alias  = "use1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project   = "washa"
      ManagedBy = "terraform"
    }
  }
}
