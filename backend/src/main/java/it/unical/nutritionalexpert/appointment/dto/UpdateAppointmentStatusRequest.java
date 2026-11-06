package it.unical.nutritionalexpert.appointment.dto;

import jakarta.validation.constraints.*;
import it.unical.nutritionalexpert.appointment.AppointmentStatus;

public record UpdateAppointmentStatusRequest(@NotNull AppointmentStatus status, @NotNull @Min(0) Long version) {}
