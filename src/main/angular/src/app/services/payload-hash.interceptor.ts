import {HttpInterceptorFn} from '@angular/common/http';
import {from, switchMap} from 'rxjs';

/**
 * Sends `x-amz-content-sha256` (hex SHA-256 of the request body) on same-origin `/api` POST/PUT/PATCH
 * requests. In prod the app is a Lambda Function URL behind CloudFront with OAC: for methods with a
 * body, CloudFront does NOT hash the payload when it signs the origin request, so the client must
 * supply the body hash or the Function URL's SigV4 check rejects the call with 403 (AWS's documented
 * OAC-for-Lambda requirement). We serialize the body here and re-set it as the exact string we
 * hashed, so the digest matches the bytes CloudFront forwards. Harmless in dev (no OAC; the header is
 * ignored) and skipped for the external currency-rate GETs (absolute URLs, no body).
 */
export const payloadHashInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.body == null || !req.url.startsWith('/api/') || !['POST', 'PUT', 'PATCH'].includes(req.method)) {
    return next(req);
  }

  const serialized = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
  const bytes = new TextEncoder().encode(serialized);

  return from(crypto.subtle.digest('SHA-256', bytes)).pipe(
      switchMap((digest) => {
        const hex = Array.from(new Uint8Array(digest))
            .map((byte) => byte.toString(16).padStart(2, '0'))
            .join('');

        return next(req.clone({
          body: serialized,
          setHeaders: {'Content-Type': 'application/json', 'x-amz-content-sha256': hex},
        }));
      }),
  );
};
