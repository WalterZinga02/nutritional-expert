/* Test automatici di auth-page.ts: verificano comportamento, chiamate HTTP simulate e gestione degli errori senza un backend reale. */
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { AuthService } from './auth.service';
import { AuthPage } from './auth-page';
import { AccountPage } from './account-page';

describe('Authentication pages', () => {
  let http: HttpTestingController;
  // Prepara un ambiente isolato e le dipendenze simulate per ogni caso.
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [
      provideRouter(routes), provideHttpClient(), provideHttpClientTesting()
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  // Verifica le richieste attese e ripulisce lo stato usato dal test.
  afterEach(() => { http.verify(); TestBed.inject(AuthService).logout(); });

  // Verifica che il visitatore anonimo venga rimandato al login.
  it('redirects an anonymous visitor away from the account', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account', AuthPage);
    expect(harness.routeNativeElement?.textContent).toContain('Bentornato');
  });

  // Verifica che password e conferma diverse blocchino la registrazione.
  it('does not submit mismatched passwords', async () => {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/registrazione', AuthPage);
    page.form.setValue({ email: 'mario@example.it', password: 'Password123!', confirmPassword: 'Different!',
      firstName: 'Mario', lastName: 'Rossi', phone: '' });
    page.submit();
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Le password non coincidono');
    http.expectNone('/api/auth/register');
  });

  // Verifica errore di login e possibilità di riprovare.
  it('shows the login error and permits another attempt', async () => {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/login', AuthPage);
    page.form.patchValue({ email: 'mario@example.it', password: 'WrongPassword' });
    page.submit();
    expect(page.busy()).toBe(true);
    http.expectOne('/api/auth/login').flush({ message: 'Email o password non corrette.' },
      { status: 401, statusText: 'Unauthorized' });
    harness.detectChanges();
    expect(page.busy()).toBe(false);
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain('Email o password non corrette.');
  });

  // Verifica registrazione, accesso al profilo e logout.
  it('registers, opens the protected account and clears the session on logout', async () => {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/registrazione', AuthPage);
    page.form.setValue({ email: 'mario@example.it', password: 'Password123!', confirmPassword: 'Password123!',
      firstName: 'Mario', lastName: 'Rossi', phone: '' });
    page.submit();
    const request = http.expectOne('/api/auth/register');
    expect(request.request.body.email).toBe('mario@example.it');
    request.flush({ accessToken: 'valid-token', tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 1800000).toISOString(),
      user: { id: 1, email: 'mario@example.it', firstName: 'Mario', lastName: 'Rossi', phone: null, role: 'USER' } });
    await harness.navigateByUrl('/account', AccountPage);
    expect(harness.routeNativeElement?.textContent).toContain('Ciao, Mario.');
    TestBed.inject(AuthService).logout();
    await harness.navigateByUrl('/login', AuthPage);
    await harness.navigateByUrl('/account', AuthPage);
    expect(TestBed.inject(AuthService).accessToken()).toBeNull();
    expect(sessionStorage.getItem('nutritional-expert.session')).toBeNull();
  });
});
