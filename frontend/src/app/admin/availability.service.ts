/* Client HTTP delle API ADMIN degli slot; le interfacce descrivono il JSON, non sono entità JPA né JWT. */
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface AvailabilitySlot {
  id: number;
  startTime: string;
  endTime: string;
  available: boolean;
  booked: boolean;
  version: number;
}
@Injectable({ providedIn: 'root' })
export class AvailabilityService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/admin/availability';
  /** Richiede gli slot nell’intervallo temporale indicato. */
  list(from: string, to: string) {
    return this.http.get<AvailabilitySlot[]>(this.url, { params: { from, to } });
  }
  /** Invia inizio e fine al backend per creare uno slot. */
  create(startTime: string, endTime: string) {
    return this.http.post<AvailabilitySlot>(this.url, { startTime, endTime });
  }
  /** Invia il nuovo flag insieme alla versione dello slot per rilevare modifiche concorrenti. */
  setAvailable(slot: AvailabilitySlot, available: boolean) {
    return this.http.patch<AvailabilitySlot>(this.url + '/' + slot.id, { available, version: slot.version });
  }
}
