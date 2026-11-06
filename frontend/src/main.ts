/* Punto di avvio Angular: monta App nella pagina usando provider, router e inizializzazione definiti in app.config. */
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
