# Local state by design. This is a single-operator, two-person project, and the state holds NO
# secrets: the SSM parameter values and the Lambda environment are managed out-of-band (see
# ../README.md "Secrets"), so nothing sensitive lands here. The state file is gitignored.
#
# To move to encrypted remote state later: create the bucket + lock table, uncomment the s3 block,
# comment out the local block, then run `terraform init -migrate-state`.
terraform {
  backend "local" {}

  # backend "s3" {
  #   bucket         = "washa-tfstate-<account-id>"
  #   key            = "washa/terraform.tfstate"
  #   region         = "ap-northeast-1"
  #   dynamodb_table = "washa-tflock"
  #   encrypt        = true
  # }
}
