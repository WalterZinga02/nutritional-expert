package it.unical.nutritionalexpert.availability.dto;

import java.time.Instant;
import it.unical.nutritionalexpert.availability.AvailabilitySlot;

public record SlotResponse(Long id, Instant startTime, Instant endTime, boolean available, boolean booked, long version) {
    public static SlotResponse from(AvailabilitySlot slot, boolean booked) {
        return new SlotResponse(slot.getId(), slot.getStartTime(), slot.getEndTime(),
                slot.isAvailable(), booked, slot.getVersion());
    }
}
