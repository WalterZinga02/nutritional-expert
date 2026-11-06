package it.unical.nutritionalexpert.appointment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelAppointmentRequest(@NotNull @Min(0) Long version) {}
