import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AvailabilitySlot } from '../admin/availability.service';

export type AppointmentType = 'WEIGHING_AND_BMI' | 'DIET_CREATION' | 'DIET_REVIEW' | 'NUTRITIONAL_CONSULTATION';
export type AppointmentStatus = 'REQUESTED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
export interface Appointment {
  id: number; slotId: number; startTime: string; endTime: string;
  type: AppointmentType; status: AppointmentStatus; createdAt: string; version: number;
}
export const VISIT_TYPES: { value: AppointmentType; label: string }[] = [
  { value: 'WEIGHING_AND_BMI', label: 'Pesata e calcolo del BMI' },
  { value: 'DIET_CREATION', label: 'Elaborazione dieta' },
  { value: 'DIET_REVIEW', label: 'Revisione dieta' },
  { value: 'NUTRITIONAL_CONSULTATION', label: 'Consulenza alimentare' }
];
export const STATUS_LABELS: Record<AppointmentStatus, string> = {
  REQUESTED: 'Richiesto', CONFIRMED: 'Confermato', COMPLETED: 'Completato', CANCELLED: 'Annullato'
};
@Injectable({ providedIn: 'root' })
export class AppointmentsService {
  private readonly http = inject(HttpClient);
  available(from: string, to: string) {
    return this.http.get<AvailabilitySlot[]>('/api/appointments/available-slots', { params: { from, to } });
  }
  book(slot: AvailabilitySlot, type: AppointmentType) {
    return this.http.post<Appointment>('/api/appointments', { slotId: slot.id, version: slot.version, type });
  }
  mine() { return this.http.get<Appointment[]>('/api/appointments/me'); }
  cancel(appointment: Appointment) {
    return this.http.patch<Appointment>(`/api/appointments/${appointment.id}/cancel`, { version: appointment.version });
  }
}
