/* Gestisce agenda e transizioni ADMIN: controlla stato, orario e versione prima di aggiornare la visita nella transazione. */
package it.unical.nutritionalexpert.appointment;

import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import it.unical.nutritionalexpert.appointment.dto.*;

@Service
public class AdminAppointmentService {
    private final AppointmentRepository appointments;
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AdminAppointmentService(AppointmentRepository appointments) { this.appointments = appointments; }

    /** Controlla il limite di 31 giorni, legge le entità e costruisce i DTO con i contatti dei clienti. */
    @Transactional(readOnly = true)
    public List<AdminAppointmentResponse> list(Instant from, Instant to, AppointmentStatus status) {
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleziona un intervallo valido di massimo 31 giorni.");
        return appointments.findForAdmin(from, to, status).stream().map(AdminAppointmentResponse::from).toList();
    }

    /** Controlla versione e transizione temporale consentita; il flush rileva anche modifiche concorrenti dell’utente. */
    @Transactional
    public AdminAppointmentResponse update(long id, UpdateAppointmentStatusRequest request) {
        Appointment appointment = appointments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Appuntamento non trovato."));
        if (appointment.getVersion() != request.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "L'appuntamento è stato modificato. Aggiorna l'elenco e riprova.");
        AppointmentStatus current = appointment.getStatus();
        AppointmentStatus next = request.status();
        boolean allowed = (current == AppointmentStatus.REQUESTED &&
                (next == AppointmentStatus.CONFIRMED || next == AppointmentStatus.CANCELLED)) ||
                (current == AppointmentStatus.CONFIRMED &&
                (next == AppointmentStatus.COMPLETED || next == AppointmentStatus.CANCELLED));
        if (!allowed)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Questo cambio di stato non è consentito.");
        Instant now = Instant.now();
        if (next == AppointmentStatus.CONFIRMED && !appointment.getSlot().getStartTime().isAfter(now))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Non puoi confermare una visita già iniziata.");
        if (next == AppointmentStatus.COMPLETED && appointment.getSlot().getEndTime().isAfter(now))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Puoi completare la visita solo dopo l'orario di fine.");
        // Cancellation preserves the record and leaves the slot disabled. Reopening is an explicit ADMIN action.
        appointment.changeStatus(next);
        appointments.flush();
        return AdminAppointmentResponse.from(appointment);
    }
}
