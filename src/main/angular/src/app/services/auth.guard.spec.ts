import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, UrlTree} from '@angular/router';
import {firstValueFrom, isObservable, Observable, of} from 'rxjs';
import {authGuard, guestGuard} from './auth.guard';

describe('auth guards', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function run(guard: typeof authGuard): Promise<boolean | UrlTree> {
    const result = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
    const asObservable = isObservable(result)
        ? (result as Observable<boolean | UrlTree>)
        : of(result as boolean | UrlTree);
    return firstValueFrom(asObservable);
  }

  it('authGuard should allow activation when signed in', async () => {
    const pending = run(authGuard);
    http.expectOne('/api/me').flush({uuid: 'u', displayName: 'Alice', email: 'a@example.com'});
    expect(await pending).toBe(true);
  });

  it('authGuard should redirect to /sso/sign-in when signed out (401)', async () => {
    const pending = run(authGuard);
    http.expectOne('/api/me').flush('no', {status: 401, statusText: 'Unauthorized'});
    expect(((await pending) as UrlTree).toString()).toBe('/sso/sign-in');
  });

  it('authGuard should redirect to /sso/sign-in when not allowlisted (403)', async () => {
    const pending = run(authGuard);
    http.expectOne('/api/me').flush('no', {status: 403, statusText: 'Forbidden'});
    expect(((await pending) as UrlTree).toString()).toBe('/sso/sign-in');
  });

  it('guestGuard should allow the sign-in page when signed out', async () => {
    const pending = run(guestGuard);
    http.expectOne('/api/me').flush('no', {status: 401, statusText: 'Unauthorized'});
    expect(await pending).toBe(true);
  });

  it('guestGuard should redirect to the dashboard when already signed in', async () => {
    const pending = run(guestGuard);
    http.expectOne('/api/me').flush({uuid: 'u', displayName: 'Alice', email: 'a@example.com'});
    expect(((await pending) as UrlTree).toString()).toBe('/');
  });
});
