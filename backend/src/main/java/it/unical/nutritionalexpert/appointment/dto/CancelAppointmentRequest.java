/* DTO di annullamento personale: contiene la versione dell’appuntamento per rilevare dati del client non aggiornati. */
package it.unical.nutritionalexpert.appointment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelAppointmentRequest(@NotNull @Min(0) Long version) {}
