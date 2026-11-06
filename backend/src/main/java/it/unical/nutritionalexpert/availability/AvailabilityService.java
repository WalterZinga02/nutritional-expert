/* Regole degli slot: intervalli futuri e senza sovrapposizioni, modifica solo di slot non prenotati e controllo della versione. */
package it.unical.nutritionalexpert.availability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import it.unical.nutritionalexpert.availability.dto.*;

@Service
public class AvailabilityService {
    private final AvailabilitySlotRepository slots;
    private final SlotAppointments appointments;
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AvailabilityService(AvailabilitySlotRepository slots, SlotAppointments appointments) {
        this.slots = slots;
        this.appointments = appointments;
    }
    /** Valida un intervallo di massimo 31 giorni e costruisce i DTO con il flag booked. */
    @Transactional(readOnly = true)
    public List<SlotResponse> list(Instant from, Instant to) {
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleziona un intervallo valido di massimo 31 giorni.");
        var booked = appointments.bookedInRange(from, to);
        return slots.findByStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(to, from)
                .stream().map(slot -> SlotResponse.from(slot, booked.contains(slot.getId()))).toList();
    }
    /** Controlla date, durata e sovrapposizioni, poi salva la nuova entità slot. */
    @Transactional
    public SlotResponse create(CreateSlotRequest request) {
        if (!request.startTime().isAfter(Instant.now()) || !request.endTime().isAfter(request.startTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'inizio deve essere futuro e la fine successiva all'inizio.");
        if (Duration.between(request.startTime(), request.endTime()).compareTo(Duration.ofHours(24)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno slot può durare al massimo 24 ore.");
        if (slots.existsByStartTimeLessThanAndEndTimeGreaterThan(request.endTime(), request.startTime()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esiste già uno slot in questo intervallo, anche se disabilitato.");
        return SlotResponse.from(slots.saveAndFlush(new AvailabilitySlot(request.startTime(), request.endTime())), false);
    }
    /** Controlla versione, orario e prenotazioni prima di cambiare disponibilità e sincronizzare JPA. */
    @Transactional
    public SlotResponse update(long id, UpdateAvailabilityRequest request) {
        AvailabilitySlot slot = slots.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot non trovato."));
        if (slot.getVersion() != request.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo slot è stato modificato. Aggiorna l'elenco e riprova.");
        if (!slot.getStartTime().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Non puoi modificare uno slot già iniziato.");
        if (appointments.isBooked(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo slot ha una prenotazione e non può essere modificato.");
        slot.setAvailable(request.available());
        slots.flush();
        return SlotResponse.from(slot, false);
    }
}
