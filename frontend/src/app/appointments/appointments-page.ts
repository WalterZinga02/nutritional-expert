/* Pagina utente: ricerca slot, invia prenotazioni, mostra lo storico e gestisce la conferma di annullamento. */
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { AvailabilitySlot } from '../admin/availability.service';
import { Appointment, AppointmentType, AppointmentsService, VISIT_TYPES, STATUS_LABELS } from './appointments.service';

/** Restituisce il giorno locale nel formato richiesto dall’input date. */
function today(): string {
  const date = new Date();
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
}
@Component({
  selector: 'app-appointments-page',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './appointments-page.html',
  styleUrl: './appointments-page.scss'
})
export class AppointmentsPage {
  private readonly api = inject(AppointmentsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private slotRequest?: Subscription;
  private appointmentsRequest?: Subscription;
  readonly day = signal(today());
  readonly minDay = today();
  readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  readonly slots = signal<AvailabilitySlot[]>([]);
  readonly appointments = signal<Appointment[]>([]);
  readonly loading = signal(false);
  readonly loadingAppointments = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly listError = signal('');
  readonly receipt = signal<Appointment | null>(null);
  readonly pendingCancel = signal<Appointment | null>(null);
  readonly cancellationMessage = signal('');
  readonly visitTypes = VISIT_TYPES;
  readonly statuses = STATUS_LABELS;
  readonly form = this.fb.nonNullable.group({
    slotId: ['', Validators.required],
    type: ['', Validators.required]
  });

  /** Carica disponibilità e storico personale all’apertura della pagina. */
  constructor() { this.loadSlots(); this.loadAppointments(); }
  /** Cambia giorno e azzera la scelta dello slot attraverso il ricaricamento. */
  changeDay(value: string): void {
    if (this.busy() || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return;
    this.day.set(value);
    this.error.set('');
    this.loadSlots();
  }
  /** Annulla richieste precedenti e carica gli slot del giorno, evitando di mantenere una selezione obsoleta. */
  loadSlots(): void {
    this.slotRequest?.unsubscribe();
    this.form.controls.slotId.reset('');
    this.slots.set([]);
    const from = new Date(this.day() + 'T00:00:00');
    if (!Number.isFinite(from.getTime())) return;
    const to = new Date(from);
    to.setDate(to.getDate() + 1);
    this.loading.set(true);
    this.slotRequest = this.api.available(from.toISOString(), to.toISOString())
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({ next: slots => this.slots.set(slots), error: error => this.error.set(this.message(error)) });
  }
  /** Ricarica lo storico e scarta eventuali conferme di annullamento basate su dati precedenti. */
  loadAppointments(): void {
    this.pendingCancel.set(null);
    this.appointmentsRequest?.unsubscribe();
    this.listError.set('');
    this.loadingAppointments.set(true);
    this.appointmentsRequest = this.api.mine()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loadingAppointments.set(false)))
      .subscribe({ next: appointments => this.appointments.set(appointments), error: error => this.listError.set(this.message(error)) });
  }
  /** Valida slot e tipo, invia la prenotazione e aggiorna ricevuta, disponibilità e storico. */
  book(): void {
    if (this.busy() || this.loading()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const slot = this.slots().find(item => String(item.id) === this.form.controls.slotId.value);
    const type = this.visitTypes.find(item => item.value === this.form.controls.type.value)?.value;
    if (!slot || !type) { this.error.set('Seleziona uno slot e il tipo di visita.'); return; }
    if (Date.parse(slot.startTime) <= Date.now()) {
      this.error.set('Questo orario è già iniziato. Scegli un nuovo slot.');
      this.loadSlots();
      return;
    }
    this.busy.set(true);
    this.pendingCancel.set(null);
    this.cancellationMessage.set('');
    this.error.set('');
    this.receipt.set(null);
    this.api.book(slot, type).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({ next: appointment => {
        this.receipt.set(appointment);
        this.loadSlots();
        this.loadAppointments();
      }, error: error => {
        this.error.set(this.message(error));
        if (error.status === 409 || error.status === 404) {
          this.loadSlots();
          this.loadAppointments();
        }
      } });
  }
  /** Mostra l’azione solo per visite future richieste o confermate; il backend ne verifica anche il proprietario. */
  canCancel(appointment: Appointment): boolean {
    return (appointment.status === 'REQUESTED' || appointment.status === 'CONFIRMED')
      && Date.parse(appointment.startTime) > Date.now();
  }
  /** Apre la conferma con i dettagli della visita senza ancora modificarla. */
  requestCancellation(appointment: Appointment): void {
    if (this.busy() || !this.canCancel(appointment)) return;
    this.error.set('');
    this.cancellationMessage.set('');
    this.pendingCancel.set(appointment);
  }
  /** Conferma l’annullamento via API e ricarica i dati dopo successo o conflitto. */
  cancel(): void {
    const appointment = this.pendingCancel();
    if (this.busy() || !appointment) return;
    this.busy.set(true);
    this.error.set('');
    this.receipt.set(null);
    this.api.cancel(appointment).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({ next: () => {
        this.cancellationMessage.set(`Appuntamento n. ${appointment.id} annullato.`);
        this.loadAppointments();
        this.loadSlots();
      }, error: error => {
        this.error.set(this.message(error));
        if (error.status === 409 || error.status === 404) {
          this.loadAppointments();
          this.loadSlots();
        }
      } });
  }
  /** Converte il valore del tipo visita nella sua etichetta italiana. */
  typeLabel(type: AppointmentType): string {
    return this.visitTypes.find(item => item.value === type)?.label ?? type;
  }
  /** Estrae il messaggio del backend o fornisce un testo per gli errori di rete. */
  private message(error: HttpErrorResponse): string {
    return error.status === 0 ? 'Il servizio non è raggiungibile. Riprova.'
      : error.error?.message || 'Operazione non riuscita. Riprova.';
  }
}
