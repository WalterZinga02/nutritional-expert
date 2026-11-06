package it.unical.nutritionalexpert.availability;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import it.unical.nutritionalexpert.availability.dto.*;

@RestController
@RequestMapping("/api/admin/availability")
public class AvailabilityController {
    private final AvailabilityService availability;
    public AvailabilityController(AvailabilityService availability) { this.availability = availability; }
    @GetMapping
    public List<SlotResponse> list(@RequestParam Instant from, @RequestParam Instant to) {
        return availability.list(from, to);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse create(@Valid @RequestBody CreateSlotRequest request) { return availability.create(request); }
    @PatchMapping("/{id}")
    public SlotResponse update(@PathVariable long id, @Valid @RequestBody UpdateAvailabilityRequest request) {
        return availability.update(id, request);
    }
}
