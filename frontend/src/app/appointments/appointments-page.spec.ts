/* Test automatici di appointments-page.ts: verificano comportamento, chiamate HTTP simulate e gestione degli errori senza un backend reale. */
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AppointmentsPage } from './appointments-page';
import { AuthService } from '../auth/auth.service';
import { authInterceptor } from '../auth/auth.interceptor';
import { Appointment } from './appointments.service';

describe('User appointments', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AppointmentsPage>;
  let auth: AuthService;
  const slot = { id: 8, startTime: new Date(Date.now() + 86400000).toISOString(),
    endTime: new Date(Date.now() + 88200000).toISOString(), available: true, booked: false, version: 2 };
  const appointment: Appointment = { id: 5, slotId: 8, startTime: slot.startTime, endTime: slot.endTime,
    type: 'DIET_REVIEW', status: 'REQUESTED', createdAt: new Date().toISOString(), version: 0 };
  // Individua la richiesta HTTP simulata da completare con i dati del test.
  const slotsRequest = () => http.expectOne(request => request.url === '/api/appointments/available-slots');
  // Prepara un ambiente isolato e le dipendenze simulate per ogni caso.
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [AppointmentsPage], providers: [
      provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()
    ] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login({ email: 'user@example.test', password: 'Password123!' }).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 'user-token', expiresAt: new Date(Date.now() + 1800000).toISOString(),
      user: { id: 1, email: 'user@example.test', firstName: 'Mario', lastName: 'Rossi', phone: null, role: 'USER' } });
    fixture = TestBed.createComponent(AppointmentsPage);
    slotsRequest().flush([slot]);
    http.expectOne('/api/appointments/me').flush([]);
    await fixture.whenStable();
  });
  // Verifica le richieste attese e ripulisce lo stato usato dal test.
  afterEach(() => { fixture.destroy(); http.verify(); auth.logout(); sessionStorage.clear(); });

  // Verifica conferma dell’annullamento, JWT, versione e ricaricamento storico.
  it('confirms cancellation, sends JWT and version and refreshes history', () => {
    const page = fixture.componentInstance;
    page.appointments.set([appointment]);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.cancel-appointment').click();
    http.expectNone('/api/appointments/5/cancel');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.cancel-confirmation .primary').click();
    const request = http.expectOne('/api/appointments/5/cancel');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ version: 0 });
    expect(request.request.headers.get('Authorization')).toBe('Bearer user-token');
    page.cancel();
    http.expectNone('/api/appointments/5/cancel');
    request.flush({ ...appointment, status: 'CANCELLED', version: 1 });
    http.expectOne('/api/appointments/me').flush([{ ...appointment, status: 'CANCELLED', version: 1 }]);
    slotsRequest().flush([]);
    fixture.detectChanges();
    expect(page.pendingCancel()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Appuntamento n. 5 annullato.');
    expect(fixture.nativeElement.querySelector('.cancel-appointment')).toBeNull();
  });
  // Verifica ricaricamento dopo conflitto e nuovo tentativo dopo errore server.
  it('refreshes on cancellation conflict and allows retry on server error', () => {
    const page = fixture.componentInstance;
    page.requestCancellation(appointment);
    page.cancel();
    http.expectOne('/api/appointments/5/cancel').flush({}, { status: 500, statusText: 'Error' });
    expect(page.busy()).toBe(false);
    expect(page.pendingCancel()).toEqual(appointment);
    page.cancel();
    http.expectOne('/api/appointments/5/cancel').flush({ message: 'Appuntamento modificato.' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/appointments/me').flush([{ ...appointment, status: 'CONFIRMED', version: 1 }]);
    slotsRequest().flush([]);
    expect(page.pendingCancel()).toBeNull();
    expect(page.cancellationMessage()).toBe('');
    expect(page.error()).toBe('Appuntamento modificato.');
  });
  // Verifica azioni consentite e chiusura della conferma senza invio.
  it('hides cancellation for past and terminal visits and lets users dismiss confirmation', () => {
    const page = fixture.componentInstance;
    expect(page.canCancel({ ...appointment, status: 'CONFIRMED' })).toBe(true);
    expect(page.canCancel({ ...appointment, status: 'COMPLETED' })).toBe(false);
    expect(page.canCancel({ ...appointment, status: 'CANCELLED' })).toBe(false);
    expect(page.canCancel({ ...appointment, startTime: new Date(Date.now() - 1000).toISOString() })).toBe(false);
    page.requestCancellation(appointment);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.cancel-confirmation .secondary').click();
    page.cancel();
    http.expectNone('/api/appointments/5/cancel');
  });
  // Verifica che slot e tipo visita siano obbligatori.
  it('requires a slot and visit type before sending', () => {
    fixture.componentInstance.book();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBe(2);
    http.expectNone('/api/appointments');
  });
  // Verifica prenotazione autenticata con versione, ricevuta e storico.
  it('books with the token and current version, then displays the receipt and personal list', () => {
    const page = fixture.componentInstance;
    page.form.setValue({ slotId: '8', type: 'DIET_REVIEW' });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = http.expectOne('/api/appointments');
    expect(request.request.headers.get('Authorization')).toBe('Bearer user-token');
    expect(request.request.body).toEqual({ slotId: 8, version: 2, type: 'DIET_REVIEW' });
    page.book();
    http.expectNone('/api/appointments');
    request.flush(appointment, { status: 201, statusText: 'Created' });
    slotsRequest().flush([]);
    http.expectOne('/api/appointments/me').flush([appointment]);
    fixture.detectChanges();
    expect(page.busy()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Richiesta di prenotazione inviata');
    expect(fixture.nativeElement.textContent).toContain('in attesa di conferma');
    expect(fixture.nativeElement.querySelector('li').textContent).toContain('Revisione dieta');
    expect(page.form.controls.slotId.value).toBe('');
  });
  // Verifica ricaricamento disponibilità dopo conflitto senza falsa ricevuta.
  it('refreshes availability on conflict without showing a successful booking', () => {
    const page = fixture.componentInstance;
    page.form.setValue({ slotId: '8', type: 'DIET_REVIEW' });
    page.book();
    http.expectOne('/api/appointments').flush({ message: 'Slot non più disponibile.' }, { status: 409, statusText: 'Conflict' });
    slotsRequest().flush([]);
    http.expectOne('/api/appointments/me').flush([]);
    fixture.detectChanges();
    expect(page.receipt()).toBeNull();
    expect(page.busy()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Slot non più disponibile');
    expect(page.form.controls.slotId.value).toBe('');
  });
  // Verifica conservazione del modulo dopo errore e possibilità di riprovare.
  it('preserves the form on service failure and allows retry', () => {
    const page = fixture.componentInstance;
    page.form.setValue({ slotId: '8', type: 'DIET_REVIEW' });
    page.book();
    http.expectOne('/api/appointments').flush({}, { status: 500, statusText: 'Server error' });
    expect(page.busy()).toBe(false);
    expect(page.form.controls.slotId.value).toBe('8');
    page.book();
    http.expectOne('/api/appointments').flush(appointment);
    slotsRequest().flush([]);
    http.expectOne('/api/appointments/me').flush([appointment]);
  });
  // Verifica reset dello slot e interruzione delle letture al cambio giorno.
  it('clears the selected slot when changing days and cancels obsolete availability requests', () => {
    const page = fixture.componentInstance;
    page.form.controls.slotId.setValue('8');
    page.changeDay('2030-06-02');
    const obsolete = slotsRequest();
    page.changeDay('2030-06-03');
    expect(obsolete.cancelled).toBe(true);
    slotsRequest().flush([]);
    expect(page.form.controls.slotId.value).toBe('');
    expect(page.slots()).toEqual([]);
  });
  // Verifica logout quando la prenotazione riceve 401.
  it('clears the session if booking is rejected with 401', () => {
    const page = fixture.componentInstance;
    page.form.setValue({ slotId: '8', type: 'DIET_REVIEW' });
    page.book();
    http.expectOne('/api/appointments').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.user()).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: '1' } });
  });
});
