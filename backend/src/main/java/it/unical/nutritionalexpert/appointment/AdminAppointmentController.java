/* API ADMIN per consultare le prenotazioni e richiedere cambi di stato tramite DTO validati. */
package it.unical.nutritionalexpert.appointment;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import it.unical.nutritionalexpert.appointment.dto.*;

@RestController
@RequestMapping("/api/admin/appointments")
public class AdminAppointmentController {
    private final AdminAppointmentService service;
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AdminAppointmentController(AdminAppointmentService service) { this.service = service; }
    /** Riceve intervallo e filtro stato opzionale e restituisce l’agenda ADMIN. */
    @GetMapping
    public List<AdminAppointmentResponse> list(@RequestParam Instant from, @RequestParam Instant to,
                                              @RequestParam(required = false) AppointmentStatus status) {
        return service.list(from, to, status);
    }
    /** Inoltra ID, stato desiderato e versione al service dopo la validazione del DTO. */
    @PatchMapping("/{id}/status")
    public AdminAppointmentResponse update(@PathVariable long id, @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return service.update(id, request);
    }
}
