import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AppointmentsPage } from './appointments-page';
import { AuthService } from '../auth/auth.service';
import { authInterceptor } from '../auth/auth.interceptor';

describe('User appointments', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AppointmentsPage>;
  let auth: AuthService;
  const slot = { id: 8, startTime: new Date(Date.now() + 86400000).toISOString(),
    endTime: new Date(Date.now() + 88200000).toISOString(), available: true, booked: false, version: 2 };
  const appointment = { id: 5, slotId: 8, startTime: slot.startTime, endTime: slot.endTime,
    type: 'DIET_REVIEW', status: 'REQUESTED', createdAt: new Date().toISOString(), version: 0 };
  const slotsRequest = () => http.expectOne(request => request.url === '/api/appointments/available-slots');
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
  afterEach(() => { fixture.destroy(); http.verify(); auth.logout(); sessionStorage.clear(); });

  it('requires a slot and visit type before sending', () => {
    fixture.componentInstance.book();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBe(2);
    http.expectNone('/api/appointments');
  });
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
  it('clears the session if booking is rejected with 401', () => {
    const page = fixture.componentInstance;
    page.form.setValue({ slotId: '8', type: 'DIET_REVIEW' });
    page.book();
    http.expectOne('/api/appointments').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.user()).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: '1' } });
  });
});
