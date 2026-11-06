/* Test automatici di availability-page.ts: verificano comportamento, chiamate HTTP simulate e gestione degli errori senza un backend reale. */
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { AvailabilityPage } from './availability-page';
import { adminGuard } from './admin.guard';
import { AuthService } from '../auth/auth.service';
import { authInterceptor } from '../auth/auth.interceptor';

describe('ADMIN availability', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AvailabilityPage>;
  let auth: AuthService;
  const slot = { id: 4, startTime: '2030-06-02T09:00:00Z', endTime: '2030-06-02T09:30:00Z',
    available: true, booked: false, version: 3 };
  // Individua la richiesta HTTP simulata da completare con i dati del test.
  const listRequest = () => http.expectOne(request => request.url === '/api/admin/availability' && request.method === 'GET');
  // Prepara un ambiente isolato e le dipendenze simulate per ogni caso.
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [AvailabilityPage], providers: [
      provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()
    ] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login({ email: 'admin@example.test', password: 'Password123!' }).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 'admin-token', expiresAt: new Date(Date.now() + 1800000).toISOString(),
      user: { id: 1, email: 'admin@example.test', firstName: 'Admin', lastName: 'Test', phone: null, role: 'ADMIN' } });
    fixture = TestBed.createComponent(AvailabilityPage);
    listRequest().flush([]);
    await fixture.whenStable();
  });
  // Verifica le richieste attese e ripulisce lo stato usato dal test.
  afterEach(() => { fixture.destroy(); http.verify(); auth.logout(); sessionStorage.clear(); });

  // Verifica conversione degli orari locali, creazione e aggiornamento agenda.
  it('creates slots using local date conversion and refreshes the agenda', () => {
    const page = fixture.componentInstance;
    page.changeDay('2030-06-02');
    listRequest().flush([]);
    page.form.setValue({ start: '09:00', end: '09:30' });
    page.create();
    const request = http.expectOne(request => request.url === '/api/admin/availability' && request.method === 'POST');
    expect(request.request.headers.get('Authorization')).toBe('Bearer admin-token');
    expect(request.request.body.startTime).toBe(new Date('2030-06-02T09:00').toISOString());
    page.create();
    http.expectNone(request => request.method === 'POST');
    request.flush(slot);
    listRequest().flush([slot]);
    expect(page.success()).toBe('Slot creato.');
    expect(page.slots().length).toBe(1);
  });
  // Verifica il blocco di intervalli temporali non validi prima dell’invio.
  it('rejects invalid time order without sending a creation request', () => {
    const page = fixture.componentInstance;
    page.changeDay('2030-06-02');
    listRequest().flush([]);
    page.form.setValue({ start: '10:00', end: '09:30' });
    page.create();
    expect(page.error()).toContain('fine successiva');
    http.expectNone(request => request.method === 'POST');
  });
  // Verifica invio della versione e ricaricamento dopo 409.
  it('sends the displayed version and refreshes on conflict', () => {
    const page = fixture.componentInstance;
    page.toggle(slot);
    const request = http.expectOne('/api/admin/availability/4');
    expect(request.request.body).toEqual({ available: false, version: 3 });
    request.flush({ message: 'Slot modificato.' }, { status: 409, statusText: 'Conflict' });
    listRequest().flush([{ ...slot, available: false, version: 4 }]);
    expect(page.error()).toBe('Slot modificato.');
    expect(page.slots()[0].version).toBe(4);
  });
  // Verifica il blocco dei comandi per slot passati o prenotati.
  it('does not modify a booked or past slot', () => {
    const page = fixture.componentInstance;
    page.toggle({ ...slot, booked: true });
    page.toggle({ ...slot, startTime: '2000-01-01T09:00:00Z' });
    http.expectNone(request => request.method === 'PATCH');
  });
  // Verifica accesso ADMIN e reindirizzamento di USER e visitatori anonimi.
  it('guard admits ADMIN, redirects USER and redirects anonymous visitors', () => {
    const runGuard = () => TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(runGuard()).toBe(true);
    auth.login({ email: 'user@example.test', password: 'Password123!' }).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 'user-token', expiresAt: new Date(Date.now() + 1800000).toISOString(),
      user: { id: 2, role: 'USER' } });
    expect(TestBed.inject(Router).serializeUrl(runGuard() as UrlTree)).toBe('/account');
    auth.logout();
    expect(TestBed.inject(Router).serializeUrl(runGuard() as UrlTree)).toBe('/login');
  });
});
