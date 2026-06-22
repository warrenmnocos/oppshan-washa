import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {Dashboard} from './dashboard';

describe('Dashboard', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should render a Budget app card linking to /budget', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne('/api/me').flush({uuid: 'u', displayName: 'Alice Example', email: 'a@example.com'});
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const card = element.querySelector('a.appcard');
    expect(card?.getAttribute('href')).toBe('/budget');
    expect(card?.textContent).toContain('Budget');
  });

  it('should show the signed-in display name', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne('/api/me').flush({uuid: 'u', displayName: 'Alice Example', email: 'a@example.com'});
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Alice Example');
  });

  it('should still render the Budget card when /api/me fails', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne('/api/me').flush('no', {status: 401, statusText: 'Unauthorized'});
    fixture.detectChanges();

    expect(fixture.componentInstance.user()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('a.appcard')?.getAttribute('href'))
        .toBe('/budget');
  });
});
