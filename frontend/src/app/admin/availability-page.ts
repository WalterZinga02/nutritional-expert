import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, Subscription } from 'rxjs';
import { AvailabilityService, AvailabilitySlot } from './availability.service';

export function localDay(date = new Date()): string {
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
}
@Component({
  selector: 'app-availability-page',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './availability-page.html',
  styleUrl: './availability-page.scss'
})
export class AvailabilityPage {
  private readonly api = inject(AvailabilityService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private listRequest?: Subscription;
  readonly day = signal(localDay());
  readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  readonly slots = signal<AvailabilitySlot[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly form = this.fb.nonNullable.group({
    start: ['09:00', Validators.required],
    end: ['09:30', Validators.required]
  });

  constructor() { this.load(); }
  changeDay(value: string): void {
    if (this.busy() || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return;
    this.day.set(value);
    this.error.set('');
    this.success.set('');
    this.load();
  }
  load(): void {
    this.listRequest?.unsubscribe();
    const from = new Date(this.day() + 'T00:00:00');
    if (!Number.isFinite(from.getTime())) return;
    const to = new Date(from);
    to.setDate(to.getDate() + 1);
    this.loading.set(true);
    this.slots.set([]);
    this.listRequest = this.api.list(from.toISOString(), to.toISOString())
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({ next: slots => this.slots.set(slots), error: error => this.showError(error) });
  }
  create(): void {
    if (this.busy() || this.loading()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error.set('');
    this.success.set('');
    const start = new Date(this.day() + 'T' + this.form.controls.start.value);
    const end = new Date(this.day() + 'T' + this.form.controls.end.value);
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) ||
        start.getTime() <= Date.now() || end <= start) {
      this.error.set("Scegli un inizio futuro e una fine successiva all'inizio.");
      return;
    }
    // Local times skipped by daylight-saving transitions must not be silently shifted.
    if (this.clock(start) !== this.form.controls.start.value || this.clock(end) !== this.form.controls.end.value) {
      this.error.set('Questo orario non esiste nel fuso selezionato a causa del cambio di ora.');
      return;
    }
    this.busy.set(true);
    this.api.create(start.toISOString(), end.toISOString())
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({ next: () => { this.success.set('Slot creato.'); this.load(); },
        error: error => { this.showError(error); if (error.status === 409) this.load(); } });
  }
  toggle(slot: AvailabilitySlot): void {
    if (this.busy() || this.loading() || !this.canChange(slot)) return;
    this.error.set('');
    this.success.set('');
    this.busy.set(true);
    this.api.setAvailable(slot, !slot.available)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({ next: () => {
        this.success.set(slot.available ? 'Slot disabilitato.' : 'Slot riattivato.');
        this.load();
      }, error: error => { this.showError(error); if (error.status === 409) this.load(); } });
  }
  canChange(slot: AvailabilitySlot): boolean {
    return !slot.booked && Date.parse(slot.startTime) > Date.now();
  }
  private clock(date: Date): string {
    return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
  }
  private showError(error: HttpErrorResponse): void {
    this.error.set(error.status === 0 ? 'Il servizio non è raggiungibile. Riprova.'
      : error.error?.message || 'Operazione non riuscita. Riprova.');
  }
}
