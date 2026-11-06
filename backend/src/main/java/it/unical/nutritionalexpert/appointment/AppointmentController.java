/* API utente delle prenotazioni: usa il soggetto del JWT come identità e delega ricerca, prenotazione, storico e annullamento. */
package it.unical.nutritionalexpert.appointment;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import it.unical.nutritionalexpert.appointment.dto.*;
import it.unical.nutritionalexpert.availability.dto.SlotResponse;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService service;
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AppointmentController(AppointmentService service) { this.service = service; }
    /** Restituisce gli slot prenotabili all’utente autenticato nell’intervallo richiesto. */
    @GetMapping("/available-slots")
    public List<SlotResponse> available(@AuthenticationPrincipal Jwt jwt, @RequestParam Instant from, @RequestParam Instant to) {
        return service.available(jwt.getSubject(), from, to);
    }
    /** Riceve il DTO di prenotazione e restituisce la visita creata con HTTP 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookAppointmentRequest request) {
        return service.book(jwt.getSubject(), request);
    }
    /** Espone solo le visite appartenenti al soggetto del JWT. */
    @GetMapping("/me")
    public List<AppointmentResponse> mine(@AuthenticationPrincipal Jwt jwt) { return service.mine(jwt.getSubject()); }
    /** Riceve ID e versione e richiede l’annullamento per il proprietario autenticato. */
    @PatchMapping("/{id}/cancel")
    public AppointmentResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                      @Valid @RequestBody CancelAppointmentRequest request) {
        return service.cancel(jwt.getSubject(), id, request);
    }
}
