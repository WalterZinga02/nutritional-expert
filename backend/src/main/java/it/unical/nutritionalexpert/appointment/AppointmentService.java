/* Logica delle prenotazioni personali: controlla proprietario e disponibilità, prenota atomicamente e annulla con locking ottimistico. */
package it.unical.nutritionalexpert.appointment;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import it.unical.nutritionalexpert.user.*;
import it.unical.nutritionalexpert.availability.*;
import it.unical.nutritionalexpert.availability.dto.SlotResponse;
import it.unical.nutritionalexpert.appointment.dto.*;

@Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final AvailabilitySlotRepository slots;
    private final SlotAppointments slotAppointments;
    private final UserRepository users;

    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AppointmentService(AppointmentRepository appointments, AvailabilitySlotRepository slots,
                              SlotAppointments slotAppointments, UserRepository users) {
        this.appointments = appointments;
        this.slots = slots;
        this.slotAppointments = slotAppointments;
        this.users = users;
    }
    /** Esclude slot passati, disabilitati o già prenotati, dopo aver verificato l’account e l’intervallo. */
    @Transactional(readOnly = true)
    public List<SlotResponse> available(String subject, Instant from, Instant to) {
        /** Ricava l’account dal subject del token e rifiuta con 401 account assenti o ID non validi. */
        currentUser(subject);
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleziona un intervallo valido di massimo 31 giorni.");
        Instant now = Instant.now();
        var booked = slotAppointments.bookedInRange(from, to);
        return slots.findByStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(to, from).stream()
                .filter(slot -> slot.isAvailable() && slot.getStartTime().isAfter(now) && !booked.contains(slot.getId()))
                .map(slot -> SlotResponse.from(slot, false)).toList();
    }
    /** Occupa lo slot con @Version prima di inserire la visita: entrambe le scritture riescono o vengono annullate insieme. */
    @Transactional
    public AppointmentResponse book(String subject, BookAppointmentRequest request) {
        User user = currentUser(subject);
        AvailabilitySlot slot = slots.findById(request.slotId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot non trovato."));
        if (!slot.isAvailable() || !slot.getStartTime().isAfter(Instant.now()) ||
                slot.getVersion() != request.version() ||
                appointments.existsBySlotIdAndStatusNot(slot.getId(), AppointmentStatus.CANCELLED))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo slot non è più disponibile o è stato modificato. Scegli un nuovo orario.");
        // Claim the slot before inserting the appointment. Both changes commit or roll back together.
        // @Version also conflicts with the ADMIN's concurrent availability changes.
        slot.setAvailable(false);
        slots.flush();
        Appointment appointment = appointments.saveAndFlush(new Appointment(user, slot, request.type()));
        return AppointmentResponse.from(appointment);
    }
    /** Legge le entità del proprietario e le trasforma nei DTO dello storico personale. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> mine(String subject) {
        User user = currentUser(subject);
        return appointments.findByUserIdOrderBySlotStartTimeDescIdDesc(user.getId()).stream()
                .map(AppointmentResponse::from).toList();
    }
    /** Verifica proprietario, versione e visita futura attiva, poi imposta CANCELLED mantenendo storico e slot disabilitato. */
    @Transactional
    public AppointmentResponse cancel(String subject, Long id, CancelAppointmentRequest request) {
        User user = currentUser(subject);
        Appointment appointment = appointments.findByIdAndUserId(id, user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Appuntamento non trovato."));
        if (appointment.getVersion() != request.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appuntamento modificato. Aggiorna l'elenco e riprova.");
        if ((appointment.getStatus() != AppointmentStatus.REQUESTED && appointment.getStatus() != AppointmentStatus.CONFIRMED)
                || !appointment.getSlot().getStartTime().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Puoi annullare solo visite richieste o confermate prima dell'inizio.");
        appointment.changeStatus(AppointmentStatus.CANCELLED);
        // Preserve history and leave reopening the slot to the studio. @Version protects ADMIN races too.
        appointments.flush();
        return AppointmentResponse.from(appointment);
    }
    /** Ricava l’account dal subject del token e rifiuta con 401 account assenti o ID non validi. */
    private User currentUser(String subject) {
        try {
            return users.findById(Long.parseLong(subject)).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non valida."));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non valida.");
        }
    }
}
