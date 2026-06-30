import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {AppHeader} from './app-header';

describe('AppHeader', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({lang: 'en'}),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should render the brand and a sign-out link even when signed out', () => {
    const fixture = TestBed.createComponent(AppHeader);
    fixture.detectChanges();
    http.expectOne('/api/me').flush('no', {status: 401, statusText: 'Unauthorized'});
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.brand-name')?.textContent).toContain('washa');
    expect(fixture.nativeElement.querySelector('a.signout')?.getAttribute('href')).toBe('/sso/sign-out');
    expect(fixture.nativeElement.querySelector('.who')).toBeNull();
  });

  it('should show the signed-in display name', () => {
    const fixture = TestBed.createComponent(AppHeader);
    fixture.detectChanges();
    http.expectOne('/api/me').flush({uuid: 'u', displayName: 'Alice Example', email: 'a@example.com'});
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.who')?.textContent).toContain('Alice Example');
  });
});
