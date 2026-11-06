/* API ADMIN degli slot: riceve date e DTO validati e delega creazione, elenco e disponibilità al service. */
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
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AvailabilityController(AvailabilityService availability) { this.availability = availability; }
    /** Espone l’elenco slot per intervallo temporale. */
    @GetMapping
    public List<SlotResponse> list(@RequestParam Instant from, @RequestParam Instant to) {
        return availability.list(from, to);
    }
    /** Riceve il DTO e restituisce lo slot creato con HTTP 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse create(@Valid @RequestBody CreateSlotRequest request) { return availability.create(request); }
    /** Inoltra ID e DTO per attivare o disattivare uno slot. */
    @PatchMapping("/{id}")
    public SlotResponse update(@PathVariable long id, @Valid @RequestBody UpdateAvailabilityRequest request) {
        return availability.update(id, request);
    }
}
