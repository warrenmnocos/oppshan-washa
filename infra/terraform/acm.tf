# TLS certificate for the public domain. MUST live in us-east-1 for CloudFront, hence aws.use1.
resource "aws_acm_certificate" "washa" {
  provider          = aws.use1
  domain_name       = var.domain_name
  validation_method = "DNS"
  # ECDSA P-384: smaller key + faster TLS handshakes than RSA-2048; CloudFront supports it for viewer
  # certs and modern clients do too. Omitting this defaults to RSA-2048.
  key_algorithm     = "EC_secp384r1"

  lifecycle {
    create_before_destroy = true
  }
}

# DNS-validation records, written into the existing hosted zone (looked up in route53.tf).
resource "aws_route53_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.washa.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }

  zone_id         = data.aws_route53_zone.washa.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

# Block apply until the cert is validated, so CloudFront can attach it.
resource "aws_acm_certificate_validation" "washa" {
  provider                = aws.use1
  certificate_arn         = aws_acm_certificate.washa.arn
  validation_record_fqdns = [for r in aws_route53_record.acm_validation : r.fqdn]
}
