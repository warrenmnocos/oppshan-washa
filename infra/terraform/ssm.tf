# SSM parameter "slots". Terraform owns each parameter's existence (name, type, tier, KMS) but NEVER
# its value: the value is a placeholder and lifecycle ignores it. Real values are pushed out-of-band
# by infra/cli/seed-secrets.sh, so no secret ever lands in Terraform state. The matching Lambda env
# var for each is materialized by infra/cli/set-lambda-env.sh. See ../README.md "Secrets".
#
# Names and types mirror the oppshan-files convention: flat, literal env-var names under the app
# prefix; SecureString for the secrets and PII, String for the non-secret datasource URL/username.
locals {
  ssm_parameters = {
    google_client_id        = { name = "${var.ssm_prefix}/GOOGLE_CLIENT_ID", type = "SecureString" }
    google_client_secret    = { name = "${var.ssm_prefix}/GOOGLE_CLIENT_SECRET", type = "SecureString" }
    token_encryption_secret = { name = "${var.ssm_prefix}/TOKEN_ENCRYPTION_SECRET", type = "SecureString" }
    datasource_jdbc_url     = { name = "${var.ssm_prefix}/QUARKUS_DATASOURCE_JDBC_URL", type = "String" }
    datasource_username     = { name = "${var.ssm_prefix}/QUARKUS_DATASOURCE_USERNAME", type = "String" }
    datasource_password     = { name = "${var.ssm_prefix}/QUARKUS_DATASOURCE_PASSWORD", type = "SecureString" }
    allowed_identities      = { name = "${var.ssm_prefix}/OPPSHAN_WASHA_ALLOWED_IDENTITIES", type = "SecureString" }
    flyway_jdbc_url         = { name = "${var.ssm_prefix}/QUARKUS_FLYWAY_JDBC_URL", type = "String" }
    flyway_username         = { name = "${var.ssm_prefix}/QUARKUS_FLYWAY_USERNAME", type = "String" }
    flyway_password         = { name = "${var.ssm_prefix}/QUARKUS_FLYWAY_PASSWORD", type = "SecureString" }
  }
}

resource "aws_ssm_parameter" "washa" {
  for_each = local.ssm_parameters

  name  = each.value.name
  type  = each.value.type
  value = "REPLACE_ME" # placeholder only — the real value is set by infra/cli/seed-secrets.sh
  tier  = "Standard"
  # SecureString parameters use the account's alias/aws/ssm key by default.

  lifecycle {
    ignore_changes = [value]
  }
}
