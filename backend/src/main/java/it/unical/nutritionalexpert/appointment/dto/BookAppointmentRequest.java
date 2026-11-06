package it.unical.nutritionalexpert.appointment.dto;

import jakarta.validation.constraints.*;
import it.unical.nutritionalexpert.appointment.AppointmentType;

public record BookAppointmentRequest(
        @NotNull @Positive Long slotId,
        @NotNull @Min(0) Long version,
        @NotNull(message = "Seleziona il tipo di visita.") AppointmentType type) {}
