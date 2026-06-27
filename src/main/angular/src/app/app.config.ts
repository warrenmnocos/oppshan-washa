import {ApplicationConfig, inject, provideAppInitializer, provideZonelessChangeDetection} from '@angular/core';
import {provideRouter, withInMemoryScrolling} from '@angular/router';
import {provideHttpClient, withFetch} from '@angular/common/http';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';
import {APP_ROUTES} from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideHttpClient(withFetch()),
    provideRouter(APP_ROUTES, withInMemoryScrolling({scrollPositionRestoration: 'enabled'})),
    provideTranslateService({
      lang: 'en',
      fallbackLang: 'en',
      loader: provideTranslateHttpLoader({prefix: '/i18n/', suffix: '.json'}),
    }),
    // Block bootstrap until en.json has loaded. Without this the first render shows raw i18n keys
    // (budget.page.title, …) for a beat before the translations arrive and snap into place — a flash
    // the static prototype never has (its strings are literal HTML). use('en') completes once the
    // file is fetched; returning its Observable holds the app's first paint until then.
    provideAppInitializer(() => inject(TranslateService).use('en')),
  ],
};
