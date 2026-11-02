import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, tap } from 'rxjs';

export interface UserProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: 'USER' | 'ADMIN';
}
export interface Credentials { email: string; password: string; }
export interface Registration extends Credentials { firstName: string; lastName: string; phone: string; }
export interface AuthResponse { accessToken: string; tokenType: string; expiresAt: string; user: UserProfile; }
const STORAGE_KEY = 'nutritional-expert.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly currentUser = signal<UserProfile | null>(null);
  readonly user = this.currentUser.asReadonly();
  private token: string | null = null;
  private expiresAt = 0;
  private expiryTimer?: ReturnType<typeof setTimeout>;

  accessToken(): string | null {
    return this.expiresAt > Date.now() ? this.token : null;
  }
  login(data: Credentials) {
    return this.http.post<AuthResponse>('/api/auth/login', data).pipe(tap(response => this.accept(response)));
  }
  register(data: Registration) {
    return this.http.post<AuthResponse>('/api/auth/register', data).pipe(tap(response => this.accept(response)));
  }
  async restore(): Promise<void> {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const stored = JSON.parse(raw) as { accessToken: string; expiresAt: string };
      const expiry = Date.parse(stored.expiresAt);
      if (typeof stored.accessToken !== 'string' || !Number.isFinite(expiry) || expiry <= Date.now()) {
        this.clear();
        return;
      }
      this.token = stored.accessToken;
      this.expiresAt = expiry;
      const user = await firstValueFrom(this.http.get<UserProfile>('/api/auth/me'));
      this.currentUser.set(user);
      this.scheduleExpiry();
    } catch {
      this.clear();
    }
  }
  logout(expired = false): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: expired ? { expired: '1' } : {} });
  }
  private accept(response: AuthResponse): void {
    this.token = response.accessToken;
    this.expiresAt = Date.parse(response.expiresAt);
    this.currentUser.set(response.user);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
        accessToken: response.accessToken, expiresAt: response.expiresAt
      }));
    } catch { /* The current in-memory session still works when storage is disabled. */ }
    this.scheduleExpiry();
  }
  private scheduleExpiry(): void {
    clearTimeout(this.expiryTimer);
    this.expiryTimer = setTimeout(() => this.logout(true), Math.max(0, this.expiresAt - Date.now()));
  }
  private clear(): void {
    clearTimeout(this.expiryTimer);
    this.token = null;
    this.expiresAt = 0;
    this.currentUser.set(null);
    try { sessionStorage.removeItem(STORAGE_KEY); } catch { /* Storage may be disabled. */ }
  }
}
