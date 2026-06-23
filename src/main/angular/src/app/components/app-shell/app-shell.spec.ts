import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {AppShell} from './app-shell';

describe('AppShell', () => {

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

  it('should frame the content with a fixed header and footer', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    // The embedded header fetches the current user.
    http.expectOne('/api/me').flush('no', {status: 401, statusText: 'Unauthorized'});
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.topbar')).toBeTruthy();
    expect(host.querySelector('.shell-content')).toBeTruthy();
    expect(host.querySelector('.foot')).toBeTruthy();
  });
});
