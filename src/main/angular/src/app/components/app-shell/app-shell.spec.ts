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

    // The native pull-to-refresh shape: the content follows the finger raw mid-pull, holds the settle
    // gap beneath the spinner while the reload runs, and snaps back to rest when it finishes.
    it('should slide the content down with the pull and hold it while a refresh spins', () => {
        const fixture = TestBed.createComponent(AppShell);
        fixture.detectChanges();
        http.expectOne('/api/me').flush('no', {status: 401, statusText: 'Unauthorized'});
        const shell = fixture.componentInstance;
        const host = fixture.nativeElement as HTMLElement;
        const content = () => host.querySelector('.ptr-content') as HTMLElement;

        // Mid-pull: the offset tracks the finger and the snap transition is off.
        shell.pullRefresh.enabled.set(true);
        shell.tracking.set(true);
        shell.pullDistance.set(40);
        fixture.detectChanges();
        expect(content().style.marginTop).toBe('40px');
        expect(content().classList.contains('snap')).toBe(false);

        // Released past the threshold: the reload spins and the content holds the settle gap, animated.
        shell.tracking.set(false);
        shell.pullDistance.set(0);
        shell.spinning.set(true);
        fixture.detectChanges();
        expect(content().style.marginTop).toBe('56px');
        expect(content().classList.contains('snap')).toBe(true);

        // Reload finished: back to rest.
        shell.spinning.set(false);
        fixture.detectChanges();
        expect(content().style.marginTop).toBe('0px');
    });
});
