/* DTO degli slot inviato al frontend: distingue disponibilità dello studio e presenza di una prenotazione, includendo la versione. */
package it.unical.nutritionalexpert.availability.dto;

import java.time.Instant;
import it.unical.nutritionalexpert.availability.AvailabilitySlot;

public record SlotResponse(Long id, Instant startTime, Instant endTime, boolean available, boolean booked, long version) {
    /** Converte l’entità JPA nel DTO di risposta, selezionando solo i campi da esporre. */
    public static SlotResponse from(AvailabilitySlot slot, boolean booked) {
        return new SlotResponse(slot.getId(), slot.getStartTime(), slot.getEndTime(),
                slot.isAvailable(), booked, slot.getVersion());
    }
}
