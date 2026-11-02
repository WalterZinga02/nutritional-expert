import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AccountPage } from './account-page';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('Profile editing', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let fixture: ComponentFixture<AccountPage>;
  const user = { id: 1, email: 'mario@example.it', firstName: 'Mario', lastName: 'Rossi', phone: '3331234567', role: 'USER' as const };

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [AccountPage], providers: [
      provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()
    ] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login({ email: user.email, password: 'Password123!' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      accessToken: 'profile-token', tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 1800000).toISOString(), user
    });
    fixture = TestBed.createComponent(AccountPage);
    await fixture.whenStable();
  });
  afterEach(() => { http.verify(); auth.logout(); sessionStorage.clear(); });

  it('prefills the form and cancels without saving', () => {
    fixture.nativeElement.querySelector('button.primary').click();
    fixture.detectChanges();
    expect(fixture.componentInstance.form.getRawValue()).toEqual({
      firstName: 'Mario', lastName: 'Rossi', phone: '3331234567'
    });
    fixture.componentInstance.form.controls.firstName.setValue('Discarded');
    fixture.nativeElement.querySelector('form button.secondary').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(auth.user()?.firstName).toBe('Mario');
    http.expectNone('/api/auth/me');
    fixture.componentInstance.startEditing();
    expect(fixture.componentInstance.form.controls.firstName.value).toBe('Mario');
  });

  it('saves through the protected API and updates the displayed profile', () => {
    const page = fixture.componentInstance;
    page.startEditing();
    page.form.setValue({ firstName: ' Anna ', lastName: ' Verdi ', phone: '' });
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = http.expectOne('/api/auth/me');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.headers.get('Authorization')).toBe('Bearer profile-token');
    expect(request.request.body).toEqual({ firstName: 'Anna', lastName: 'Verdi', phone: '' });
    page.save();
    http.expectNone('/api/auth/me');
    request.flush({ ...user, firstName: 'Anna', lastName: 'Verdi', phone: null });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Anna');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('aggiornato');
    expect(fixture.nativeElement.textContent).toContain('Non indicato');
    expect(auth.accessToken()).toBe('profile-token');
  });

  it('rejects empty names and invalid phone without making a request', () => {
    const page = fixture.componentInstance;
    page.startEditing();
    page.form.setValue({ firstName: ' ', lastName: '', phone: 'invalid' });
    page.save();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBe(3);
    http.expectNone('/api/auth/me');
  });

  it('keeps the saved profile and edited inputs after an error and permits retry', () => {
    const page = fixture.componentInstance;
    page.startEditing();
    page.form.controls.firstName.setValue('Anna');
    page.save();
    http.expectOne('/api/auth/me').flush({ message: 'Salvataggio non disponibile.' },
      { status: 500, statusText: 'Server error' });
    fixture.detectChanges();
    expect(auth.user()?.firstName).toBe('Mario');
    expect(page.form.controls.firstName.value).toBe('Anna');
    expect(page.busy()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Salvataggio non disponibile');
    page.save();
    http.expectOne('/api/auth/me').flush({ ...user, firstName: 'Anna' });
    expect(auth.user()?.firstName).toBe('Anna');
  });

  it('does not restore the session if a save response arrives after logout', () => {
    fixture.componentInstance.startEditing();
    fixture.componentInstance.save();
    const request = http.expectOne('/api/auth/me');
    auth.logout();
    request.flush({ ...user, firstName: 'Anna' });
    expect(auth.user()).toBeNull();
    expect(auth.accessToken()).toBeNull();
  });
});
