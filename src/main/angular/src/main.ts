import {bootstrapApplication} from '@angular/platform-browser';
import {appConfig} from './app/app.config';
import {App} from './app/app';

/**
 * Browser entry point: bootstraps the standalone root component (App) with the root providers
 * (appConfig), and logs any bootstrap failure to the console.
 */
bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
