import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {firstValueFrom, isObservable, Observable, of} from 'rxjs';
import {authGuard} from './auth.guard';

describe('authGuard', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function runGuard(): Promise<boolean> {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    const asObservable = isObservable(result) ? (result as Observable<boolean>) : of(result as boolean);
    return firstValueFrom(asObservable);
  }

  it('should allow activation when /api/me succeeds', async () => {
    const pending = runGuard();
    http.expectOne('/api/me').flush({uuid: 'u', displayName: 'Alice', email: 'a@example.com'});
    expect(await pending).toBe(true);
  });

  it('should deny activation and redirect to sign-in on 401', async () => {
    const original = window.location.href;
    const hrefSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      value: {get href() { return original; }, set href(value: string) { hrefSpy(value); }},
      configurable: true,
    });

    const pending = runGuard();
    http.expectOne('/api/me').flush('no', {status: 401, statusText: 'Unauthorized'});

    expect(await pending).toBe(false);
    expect(hrefSpy).toHaveBeenCalledWith('/sso/sign-in');
  });
});
