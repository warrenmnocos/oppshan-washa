import {ApplicationConfig, provideZonelessChangeDetection} from '@angular/core';
import {provideRouter, withInMemoryScrolling} from '@angular/router';
import {provideHttpClient, withFetch} from '@angular/common/http';
import {provideTranslateService} from '@ngx-translate/core';
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
  ],
};
