import {TestBed} from '@angular/core/testing';
import {HttpClient, provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {payloadHashInterceptor} from './payload-hash.interceptor';

describe('payloadHashInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([payloadHashInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // The digest is async, so let the resulting microtask/timer settle before asserting the request.
  const settle = () => new Promise((resolve) => setTimeout(resolve));

  it('should set x-amz-content-sha256 to the hex SHA-256 of the body on an /api POST', async () => {
    http.post('/api/budget/compute?month=2026-07', {a: 1}).subscribe();
    await settle();

    const req = httpMock.expectOne((r) => r.url.startsWith('/api/budget/compute'));
    expect(req.request.headers.get('x-amz-content-sha256')).toMatch(/^[0-9a-f]{64}$/);
    // Body and Content-Type are left untouched — Angular still serializes the object as JSON, and
    // that serialization is what we hashed, so the digest matches the bytes sent.
    expect(req.request.body).toEqual({a: 1});
    expect(req.request.headers.get('Content-Type')).toBeNull();
    req.flush({});
  });

  it('should not touch a GET (no body)', async () => {
    http.get('/api/me').subscribe();
    await settle();

    const req = httpMock.expectOne('/api/me');
    expect(req.request.headers.has('x-amz-content-sha256')).toBe(false);
    req.flush({});
  });

  it('should skip external (non-/api) requests', async () => {
    http.post('https://open.er-api.com/v6/latest/JPY', {x: 1}).subscribe();
    await settle();

    const req = httpMock.expectOne('https://open.er-api.com/v6/latest/JPY');
    expect(req.request.headers.has('x-amz-content-sha256')).toBe(false);
    req.flush({});
  });
});
