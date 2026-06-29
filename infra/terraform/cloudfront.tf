# Origin Access Control: CloudFront signs every origin request with sigv4 so the IAM-auth Function URL
# accepts it — and rejects anything that does not come through this distribution.
resource "aws_cloudfront_origin_access_control" "washa" {
  name                              = "${var.function_name}-oac"
  description                       = "OAC for the washa Lambda Function URL"
  origin_access_control_origin_type = "lambda"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# AWS-managed policies, referenced by name so the literal IDs stay out of the config.
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

locals {
  # The Function URL is "https://<id>.lambda-url.<region>.on.aws/"; CloudFront wants the bare host.
  function_url_host = replace(replace(aws_lambda_function_url.washa.function_url, "https://", ""), "/", "")
  origin_id         = "washa-lambda-url"
}

resource "aws_cloudfront_distribution" "washa" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "washa — ${var.domain_name}"
  aliases         = [var.domain_name]
  price_class     = var.price_class
  http_version    = "http2and3"

  origin {
    origin_id                = local.origin_id
    domain_name              = local.function_url_host
    origin_access_control_id = aws_cloudfront_origin_access_control.washa.id

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    # The viewer Host is dropped (AllViewerExceptHostHeader) so OAC can sign with the origin host —
    # which leaves the app unable to see its own public hostname. Supply it statically (the domain is
    # fixed) so OIDC redirect URIs and Response.seeOther build https://washa.oppshan.com. This is what
    # the proxy-address-forwarding settings in application.properties consume.
    custom_header {
      name  = "X-Forwarded-Host"
      value = var.domain_name
    }
    custom_header {
      name  = "X-Forwarded-Proto"
      value = "https"
    }
  }

  # Default: the SPA shell + hashed static assets. Cache per the origin's Cache-Control (which
  # CachingOptimized honors), GET/HEAD only.
  default_cache_behavior {
    target_origin_id         = local.origin_id
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_optimized.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    compress                 = true
  }

  # REST API: never cache; forward everything (auth cookies, body); all methods. No edge compression
  # on authenticated dynamic responses (BREACH-conservative); static assets compress in the default.
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = local.origin_id
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    compress                 = false
  }

  # OIDC sign-in/out: never cache; forward everything (sets/reads the session cookie); all methods.
  # No compression (redirect responses, and BREACH-conservative for the auth flow).
  ordered_cache_behavior {
    path_pattern             = "/sso/*"
    target_origin_id         = local.origin_id
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    compress                 = false
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.washa.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}
