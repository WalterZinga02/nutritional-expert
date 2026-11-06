/* Pagina ADMIN delle visite: filtri, azioni ammesse, conferma delle modifiche e ricaricamento dopo conflitti. */
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, Subscription } from 'rxjs';
import { AppointmentStatus, AppointmentType, STATUS_LABELS, VISIT_TYPES } from '../appointments/appointments.service';
import { AdminAppointment, AdminAppointmentsService } from './admin-appointments.service';

/** Restituisce il giorno locale per inizializzare il filtro data. */
function today(): string {
  const date = new Date();
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
}
@Component({
  selector: 'app-admin-appointments',
  imports: [DatePipe],
  templateUrl: './admin-appointments-page.html',
  styleUrl: './admin-appointments-page.scss'
})
export class AdminAppointmentsPage {
  private readonly api = inject(AdminAppointmentsService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  readonly day = signal(today());
  readonly filter = signal('');
  readonly appointments = signal<AdminAppointment[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly pending = signal<{ appointment: AdminAppointment; status: AppointmentStatus } | null>(null);
  readonly statuses = STATUS_LABELS;
  readonly statusOptions: AppointmentStatus[] = ['REQUESTED', 'CONFIRMED', 'COMPLETED', 'CANCELLED'];
  readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  /** Carica le visite del giorno all’apertura della pagina. */
  constructor() { this.load(); }
  /** Aggiorna il giorno e ricarica l’agenda. */
  changeDay(value: string): void {
    if (this.busy() || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return;
    this.day.set(value);
    this.refresh();
  }
  /** Aggiorna il filtro stato e ricarica l’agenda. */
  changeFilter(value: string): void {
    if (this.busy()) return;
    this.filter.set(value);
    this.refresh();
  }
  /** Azzera messaggi e azioni in attesa prima di ricaricare. */
  refresh(): void { this.error.set(''); this.success.set(''); this.pending.set(null); this.load(); }
  /** Interrompe la lettura precedente e richiede le visite per giorno e stato selezionati. */
  load(): void {
    this.request?.unsubscribe();
    const from = new Date(this.day() + 'T00:00:00');
    if (!Number.isFinite(from.getTime())) return;
    const to = new Date(from);
    to.setDate(to.getDate() + 1);
    this.loading.set(true);
    this.appointments.set([]);
    this.request = this.api.list(from.toISOString(), to.toISOString(), this.filter())
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({ next: data => this.appointments.set(data), error: error => this.error.set(this.message(error)) });
  }
  /** Determina le azioni mostrabili in base a stato e orario; il backend ripete la verifica. */
  allowed(appointment: AdminAppointment, status: AppointmentStatus): boolean {
    if (status === 'CANCELLED') return appointment.status === 'REQUESTED' || appointment.status === 'CONFIRMED';
    if (status === 'CONFIRMED') return appointment.status === 'REQUESTED' && Date.parse(appointment.startTime) > Date.now();
    if (status === 'COMPLETED') return appointment.status === 'CONFIRMED' && Date.parse(appointment.endTime) <= Date.now();
    return false;
  }
  /** Prepara il riepilogo di conferma senza inviare ancora la modifica. */
  choose(appointment: AdminAppointment, status: AppointmentStatus): void {
    if (this.busy() || this.loading() || !this.allowed(appointment, status)) return;
    this.pending.set({ appointment, status });
    this.error.set('');
    this.success.set('');
  }
  /** Invia il cambio di stato confermato e ricarica dopo successo o conflitto di versione. */
  apply(): void {
    const pending = this.pending();
    if (!pending || this.busy()) return;
    if (!this.allowed(pending.appointment, pending.status)) {
      this.pending.set(null);
      this.error.set("L'azione non è più disponibile. Aggiorna l'elenco.");
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api.update(pending.appointment, pending.status)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({
        next: result => {
          this.pending.set(null);
          this.success.set('Appuntamento n. ' + result.id + ': ' + this.statuses[result.status] + '.');
          this.load();
        },
        error: error => {
          this.error.set(this.message(error));
          if (error.status === 409 || error.status === 404) { this.pending.set(null); this.load(); }
        }
      });
  }
  /** Traduce il codice del tipo visita nella relativa etichetta. */
  typeLabel(type: AppointmentType): string { return VISIT_TYPES.find(item => item.value === type)?.label ?? type; }
  /** Restituisce un messaggio leggibile per errori HTTP o di connessione. */
  private message(error: HttpErrorResponse): string {
    return error.status === 0 ? 'Il servizio non è raggiungibile. Riprova.'
      : error.error?.message || 'Operazione non riuscita. Riprova.';
  }
}
