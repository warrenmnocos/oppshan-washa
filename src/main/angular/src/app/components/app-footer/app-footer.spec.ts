import {TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {AppFooter} from './app-footer';

describe('AppFooter', () => {

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideTranslateService({lang: 'en'})]});
  });

  it('should render the domain link and the current year', () => {
    const fixture = TestBed.createComponent(AppFooter);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.foot-link')?.getAttribute('href')).toContain('washa.oppshan.com');
    expect(fixture.nativeElement.textContent).toContain(String(new Date().getFullYear()));
  });
});
