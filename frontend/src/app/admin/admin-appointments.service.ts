/* Client HTTP dell’agenda ADMIN: legge visite e contatti e invia cambi di stato con la versione visualizzata. */
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AppointmentStatus, AppointmentType } from '../appointments/appointments.service';

export interface AdminAppointment {
  id: number; slotId: number; startTime: string; endTime: string;
  type: AppointmentType; status: AppointmentStatus; version: number;
  customer: { firstName: string; lastName: string; email: string; phone: string | null };
}
@Injectable({ providedIn: 'root' })
export class AdminAppointmentsService {
  private readonly http = inject(HttpClient);
  /** Legge le visite nell’intervallo aggiungendo il filtro stato solo se selezionato. */
  list(from: string, to: string, status: string) {
    const params: Record<string, string> = { from, to };
    if (status) params['status'] = status;
    return this.http.get<AdminAppointment[]>('/api/admin/appointments', { params });
  }
  /** Invia stato desiderato e versione corrente dell’appuntamento all’API ADMIN. */
  update(appointment: AdminAppointment, status: AppointmentStatus) {
    return this.http.patch<AdminAppointment>('/api/admin/appointments/' + appointment.id + '/status',
      { status, version: appointment.version });
  }
}
