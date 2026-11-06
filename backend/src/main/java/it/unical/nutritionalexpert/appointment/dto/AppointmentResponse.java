/* DTO della visita personale: viene costruito dal backend a partire dall’entità e poi serializzato in JSON per Angular. */
package it.unical.nutritionalexpert.appointment.dto;

import java.time.Instant;
import it.unical.nutritionalexpert.appointment.*;

public record AppointmentResponse(Long id, Long slotId, Instant startTime, Instant endTime,
                                  AppointmentType type, AppointmentStatus status, Instant createdAt, long version) {
    /** Converte l’entità JPA nel DTO di risposta, selezionando solo i campi da esporre. */
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getSlot().getId(),
                appointment.getSlot().getStartTime(), appointment.getSlot().getEndTime(),
                appointment.getType(), appointment.getStatus(), appointment.getCreatedAt(), appointment.getVersion());
    }
}
