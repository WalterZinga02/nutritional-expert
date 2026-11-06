package it.unical.nutritionalexpert.availability.dto;

import java.time.Instant;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record CreateSlotRequest(
        @NotNull(message = "Indica l'inizio dello slot.") @Future(message = "La data deve essere futura.") Instant startTime,
        @NotNull(message = "Indica la fine dello slot.") Instant endTime) {}
