package it.unical.nutritionalexpert.availability.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(
        @NotNull Boolean available,
        @NotNull @Min(0) Long version) {}
