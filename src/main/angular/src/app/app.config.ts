import {ApplicationConfig, inject, provideAppInitializer, provideZonelessChangeDetection} from '@angular/core';
import {provideRouter, withInMemoryScrolling} from '@angular/router';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';
import {APP_ROUTES} from './app.routes';
import {payloadHashInterceptor} from './services/payload-hash.interceptor';

/**
 * Root application providers, handed to bootstrapApplication. It sets up:
 * - zoneless change detection (state is signals, so there's no Zone.js in the loop),
 * - HttpClient with the payload-hash interceptor (adds the x-amz-content-sha256 body hash CloudFront
 *   OAC demands in prod, a no-op in dev),
 * - the router over APP_ROUTES with scroll-position restoration,
 * - ngx-translate loading /i18n/{lang}.json over HTTP, defaulting to and falling back to English,
 * - an app initializer that blocks the first paint until en.json has loaded. Without it the first
 *   render flashes raw i18n keys (budget.page.title, …) before the translations arrive and snap into
 *   place, a flicker the static prototype never has. use('en') resolves once the file is fetched, and
 *   returning its Observable holds bootstrap until then.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideHttpClient(withInterceptors([payloadHashInterceptor])),
    provideRouter(APP_ROUTES, withInMemoryScrolling({scrollPositionRestoration: 'enabled'})),
    provideTranslateService({
      lang: 'en',
      fallbackLang: 'en',
      loader: provideTranslateHttpLoader({prefix: '/i18n/', suffix: '.json'}),
    }),
    provideAppInitializer(() => inject(TranslateService).use('en')),
  ],
};
