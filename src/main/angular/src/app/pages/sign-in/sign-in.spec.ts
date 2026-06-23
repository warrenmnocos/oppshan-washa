import {TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {SignIn} from './sign-in';

describe('SignIn', () => {

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideTranslateService({lang: 'en'})]});
  });

  it('should render the Google sign-in button', () => {
    const fixture = TestBed.createComponent(SignIn);
    fixture.detectChanges();
    // No i18n JSON is loaded in the unit test, so the translate pipe echoes the key.
    expect(fixture.nativeElement.querySelector('button.google-btn')?.textContent).toContain('signIn.signInWithGoogle');
  });

  it('should not show an error notice by default', () => {
    const fixture = TestBed.createComponent(SignIn);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.error')).toBeNull();
  });

  it('should render a known message code passed via ?message', () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {search: '?message=messages.errors.accessDenied'},
    });
    const fixture = TestBed.createComponent(SignIn);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.error')?.textContent).toContain('messages.errors.accessDenied');
  });

  it('should navigate to the OIDC trigger on click', () => {
    const hrefSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {search: '', set href(value: string) { hrefSpy(value); }},
    });
    const fixture = TestBed.createComponent(SignIn);
    fixture.detectChanges();
    fixture.componentInstance.signIn();
    expect(hrefSpy).toHaveBeenCalledWith('/sso/sign-in/oidc/google');
  });
});
