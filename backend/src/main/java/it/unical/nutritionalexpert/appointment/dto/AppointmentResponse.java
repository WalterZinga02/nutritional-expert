package it.unical.nutritionalexpert.appointment.dto;

import java.time.Instant;
import it.unical.nutritionalexpert.appointment.*;

public record AppointmentResponse(Long id, Long slotId, Instant startTime, Instant endTime,
                                  AppointmentType type, AppointmentStatus status, Instant createdAt, long version) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getSlot().getId(),
                appointment.getSlot().getStartTime(), appointment.getSlot().getEndTime(),
                appointment.getType(), appointment.getStatus(), appointment.getCreatedAt(), appointment.getVersion());
    }
}
