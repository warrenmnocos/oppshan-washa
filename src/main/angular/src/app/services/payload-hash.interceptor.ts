import {HttpInterceptorFn} from '@angular/common/http';
import {from, switchMap} from 'rxjs';

/**
 * Sends `x-amz-content-sha256` (hex SHA-256 of the request body) on same-origin `/api` POST/PUT/PATCH
 * requests. In prod the app is a Lambda Function URL behind CloudFront with OAC: for methods with a
 * body, CloudFront does NOT hash the payload when it signs the origin request, so the client must
 * supply the body hash or the Function URL's SigV4 check rejects the call with 403 (AWS's documented
 * OAC-for-Lambda requirement).
 *
 * We hash the body exactly as Angular's `HttpRequest.serializeBody()` sends it — a string goes out
 * verbatim, anything else is `JSON.stringify`'d — and add ONLY the header. The body and `Content-Type`
 * are left untouched (Angular still serializes the object and sets `application/json`), so the digest
 * matches the bytes CloudFront forwards without us clobbering the request's own content type. Harmless
 * in dev (no OAC; the header is ignored) and skipped for the external currency-rate GETs.
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

        return next(req.clone({setHeaders: {'x-amz-content-sha256': hex}}));
      }),
  );
};
