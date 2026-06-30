import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {Dashboard} from './dashboard';

describe('Dashboard', () => {

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideTranslateService({lang: 'en'})],
    });
  });

  it('should render a Budget app card linking to /budget', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('a.appcard');
    expect(card?.getAttribute('href')).toBe('/budget');
  });

  it('should render the launcher hero and a placeholder for future apps', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();

    // No i18n JSON is loaded in unit tests, so the translate pipe echoes the key.
    expect(fixture.nativeElement.querySelector('.hero h1')?.textContent).toContain('dashboard.title');
    expect(fixture.nativeElement.querySelector('.appcard.soon')).toBeTruthy();
  });
});
