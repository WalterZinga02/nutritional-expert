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

    public AppointmentService(AppointmentRepository appointments, AvailabilitySlotRepository slots,
                              SlotAppointments slotAppointments, UserRepository users) {
        this.appointments = appointments;
        this.slots = slots;
        this.slotAppointments = slotAppointments;
        this.users = users;
    }
    @Transactional(readOnly = true)
    public List<SlotResponse> available(String subject, Instant from, Instant to) {
        currentUser(subject);
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleziona un intervallo valido di massimo 31 giorni.");
        Instant now = Instant.now();
        var booked = slotAppointments.bookedInRange(from, to);
        return slots.findByStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(to, from).stream()
                .filter(slot -> slot.isAvailable() && slot.getStartTime().isAfter(now) && !booked.contains(slot.getId()))
                .map(slot -> SlotResponse.from(slot, false)).toList();
    }
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
    @Transactional(readOnly = true)
    public List<AppointmentResponse> mine(String subject) {
        User user = currentUser(subject);
        return appointments.findByUserIdOrderBySlotStartTimeDescIdDesc(user.getId()).stream()
                .map(AppointmentResponse::from).toList();
    }
    private User currentUser(String subject) {
        try {
            return users.findById(Long.parseLong(subject)).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non valida."));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non valida.");
        }
    }
}
