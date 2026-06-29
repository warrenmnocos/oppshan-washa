# The hosted zone for the apex domain already exists (shared across oppshan apps) — look it up, never
# create it.
data "aws_route53_zone" "washa" {
  name         = "${var.hosted_zone_name}."
  private_zone = false
}

# Point the public hostname at CloudFront. Alias records carry no charge and need no TTL management.
resource "aws_route53_record" "a" {
  zone_id = data.aws_route53_zone.washa.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.washa.domain_name
    zone_id                = aws_cloudfront_distribution.washa.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "aaaa" {
  zone_id = data.aws_route53_zone.washa.zone_id
  name    = var.domain_name
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.washa.domain_name
    zone_id                = aws_cloudfront_distribution.washa.hosted_zone_id
    evaluate_target_health = false
  }
}
