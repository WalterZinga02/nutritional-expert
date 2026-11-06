/* DTO dell’agenda ADMIN: aggiunge i contatti del cliente ai dati della visita senza esporre le credenziali. */
package it.unical.nutritionalexpert.appointment.dto;

import java.time.Instant;
import it.unical.nutritionalexpert.appointment.*;

public record AdminAppointmentResponse(Long id, Long slotId, Instant startTime, Instant endTime,
        AppointmentType type, AppointmentStatus status, long version, Customer customer) {
    public record Customer(String firstName, String lastName, String email, String phone) {}
    /** Converte l’entità JPA nel DTO di risposta, selezionando solo i campi da esporre. */
    public static AdminAppointmentResponse from(Appointment appointment) {
        var user = appointment.getUser();
        return new AdminAppointmentResponse(appointment.getId(), appointment.getSlot().getId(),
                appointment.getSlot().getStartTime(), appointment.getSlot().getEndTime(),
                appointment.getType(), appointment.getStatus(), appointment.getVersion(),
                new Customer(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone()));
    }
}
