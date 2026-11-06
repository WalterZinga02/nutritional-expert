/* Test automatici di auth.service.ts: verificano comportamento, chiamate HTTP simulate e gestione degli errori senza un backend reale. */
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService, AuthResponse } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('Authentication session', () => {
  let service: AuthService;
  let http: HttpTestingController;
  const user = { id: 1, email: 'mario@example.it', firstName: 'Mario', lastName: 'Rossi', phone: null, role: 'USER' as const };
  // Costruisce una risposta di login simulata con scadenza futura.
  const response = (): AuthResponse => ({ accessToken: 'test-token', tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 1800000).toISOString(), user });

  // Prepara un ambiente isolato e le dipendenze simulate per ogni caso.
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), provideRouter([])
    ] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  // Verifica le richieste attese e ripulisce lo stato usato dal test.
  afterEach(() => { http.verify(); service.logout(); sessionStorage.clear(); });

  // Verifica salvataggio di token e scadenza senza memorizzare il profilo.
  it('stores the session after login without persisting profile data', () => {
    service.login({ email: user.email, password: 'Password123!' }).subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(response());
    expect(service.user()).toEqual(user);
    expect(service.accessToken()).toBe('test-token');
    expect(sessionStorage.getItem('nutritional-expert.session')).not.toContain('mario');
  });

  // Verifica ripristino del profilo tramite /api/auth/me.
  it('restores the profile using the protected endpoint', async () => {
    sessionStorage.setItem('nutritional-expert.session', JSON.stringify(response()));
    const restored = service.restore();
    const request = http.expectOne('/api/auth/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush(user);
    await restored;
    expect(service.user()).toEqual(user);
  });

  // Verifica eliminazione di sessioni scadute o corrotte.
  it('rejects expired or corrupt stored sessions', async () => {
    sessionStorage.setItem('nutritional-expert.session', JSON.stringify({ ...response(), expiresAt: '2000-01-01' }));
    await service.restore();
    expect(service.accessToken()).toBeNull();
    http.expectNone('/api/auth/me');
    sessionStorage.setItem('nutritional-expert.session', '{');
    await service.restore();
    expect(sessionStorage.getItem('nutritional-expert.session')).toBeNull();
  });

  // Verifica logout e ritorno al login dopo un 401.
  it('clears a rejected token and redirects to login', async () => {
    sessionStorage.setItem('nutritional-expert.session', JSON.stringify(response()));
    const restored = service.restore();
    http.expectOne('/api/auth/me').flush({ message: 'Sessione scaduta' }, { status: 401, statusText: 'Unauthorized' });
    await restored;
    expect(service.user()).toBeNull();
    expect(service.accessToken()).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: '1' } });
  });

  // Verifica la scadenza automatica della sessione.
  it('expires the session automatically', async () => {
    vi.useFakeTimers();
    try {
      service.login({ email: user.email, password: 'Password123!' }).subscribe();
      http.expectOne('/api/auth/login').flush({ ...response(), expiresAt: new Date(Date.now() + 1000).toISOString() });
      vi.advanceTimersByTime(1001);
      expect(service.user()).toBeNull();
      expect(service.accessToken()).toBeNull();
    } finally { vi.useRealTimers(); }
  });
});
