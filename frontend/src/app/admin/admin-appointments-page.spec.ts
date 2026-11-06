import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { authInterceptor } from '../auth/auth.interceptor';
import { AdminAppointmentsPage } from './admin-appointments-page';
import { AdminAppointment } from './admin-appointments.service';

describe('ADMIN appointment management', () => {
  let fixture: ComponentFixture<AdminAppointmentsPage>;
  let http: HttpTestingController;
  let auth: AuthService;
  const appointment: AdminAppointment = { id: 8, slotId: 3, type: 'DIET_REVIEW', status: 'REQUESTED', version: 2,
    startTime: new Date(Date.now() + 86400000).toISOString(), endTime: new Date(Date.now() + 88200000).toISOString(),
    customer: { firstName: 'Mario', lastName: 'Rossi', email: 'mario@example.test', phone: null } };
  const list = () => http.expectOne(request => request.url === '/api/admin/appointments' && request.method === 'GET');
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [AdminAppointmentsPage], providers: [
      provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()
    ] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login({ email: 'admin@example.test', password: 'Password123!' }).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 'admin-token', expiresAt: new Date(Date.now() + 1800000).toISOString(),
      user: { id: 1, role: 'ADMIN' } });
    fixture = TestBed.createComponent(AdminAppointmentsPage);
    list().flush([appointment]);
    await fixture.whenStable();
  });
  afterEach(() => { fixture.destroy(); http.verify(); auth.logout(); sessionStorage.clear(); });

  it('shows customer details and requires confirmation before applying a change', () => {
    expect(fixture.nativeElement.textContent).toContain('Mario Rossi');
    const page = fixture.componentInstance;
    fixture.nativeElement.querySelector('li button.primary').click();
    fixture.detectChanges();
    expect(page.pending()?.status).toBe('CONFIRMED');
    http.expectNone('/api/admin/appointments/8/status');
    fixture.nativeElement.querySelector('.confirmation button.primary').click();
    const request = http.expectOne('/api/admin/appointments/8/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'CONFIRMED', version: 2 });
    expect(request.request.headers.get('Authorization')).toBe('Bearer admin-token');
    page.apply();
    http.expectNone('/api/admin/appointments/8/status');
    request.flush({ ...appointment, status: 'CONFIRMED', version: 3 });
    list().flush([{ ...appointment, status: 'CONFIRMED', version: 3 }]);
    expect(page.pending()).toBeNull();
    expect(page.success()).toContain('Confermato');
  });
  it('filters by status and cancels obsolete requests on day changes', () => {
    const page = fixture.componentInstance;
    page.changeFilter('CONFIRMED');
    const old = list();
    expect(old.request.params.get('status')).toBe('CONFIRMED');
    page.changeDay('2030-06-01');
    expect(old.cancelled).toBe(true);
    list().flush([]);
    expect(page.appointments()).toEqual([]);
  });
  it('shows cancellation consequences and can go back without mutation', () => {
    const page = fixture.componentInstance;
    page.choose(appointment, 'CANCELLED');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.confirmation').textContent).toContain('slot resterà disabilitato');
    fixture.nativeElement.querySelector('.confirmation button.secondary').click();
    expect(page.pending()).toBeNull();
    http.expectNone('/api/admin/appointments/8/status');
  });
  it('allows completion only after the end and no actions on terminal states', () => {
    const page = fixture.componentInstance;
    expect(page.allowed({ ...appointment, status: 'CONFIRMED' }, 'COMPLETED')).toBe(false);
    expect(page.allowed({ ...appointment, status: 'CONFIRMED', endTime: '2000-01-01T10:00:00Z' }, 'COMPLETED')).toBe(true);
    for (const status of ['COMPLETED', 'CANCELLED'] as const) {
      expect(page.allowed({ ...appointment, status }, 'CANCELLED')).toBe(false);
      expect(page.allowed({ ...appointment, status }, 'CONFIRMED')).toBe(false);
    }
  });
  it('refreshes stale data after a conflict without showing success', () => {
    const page = fixture.componentInstance;
    page.choose(appointment, 'CONFIRMED');
    page.apply();
    http.expectOne('/api/admin/appointments/8/status').flush({ message: 'Appuntamento modificato.' },
      { status: 409, statusText: 'Conflict' });
    list().flush([{ ...appointment, status: 'CANCELLED', version: 3 }]);
    expect(page.pending()).toBeNull();
    expect(page.success()).toBe('');
    expect(page.error()).toBe('Appuntamento modificato.');
    expect(page.busy()).toBe(false);
  });
  it('permits retry after a service error', () => {
    const page = fixture.componentInstance;
    page.choose(appointment, 'CONFIRMED');
    page.apply();
    http.expectOne('/api/admin/appointments/8/status').flush({}, { status: 500, statusText: 'Server error' });
    expect(page.pending()).not.toBeNull();
    expect(page.busy()).toBe(false);
    page.apply();
    http.expectOne('/api/admin/appointments/8/status').flush({ ...appointment, status: 'CONFIRMED', version: 3 });
    list().flush([]);
    expect(page.error()).toBe('');
  });
});
