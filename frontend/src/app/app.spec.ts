/* Test del componente radice: verifica marchio e collegamenti di navigazione senza avviare il backend. */
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { App } from './app';
describe('App', () => {
  // Verifica che la struttura principale mostri marchio e collegamenti account.
  it('renders the brand and account links', async () => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Nutritional Expert');
    expect(fixture.nativeElement.querySelector('a[href="/registrazione"]')).toBeTruthy();
  });
});
