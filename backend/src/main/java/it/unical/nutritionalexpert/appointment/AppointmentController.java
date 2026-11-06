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
    public AppointmentController(AppointmentService service) { this.service = service; }
    @GetMapping("/available-slots")
    public List<SlotResponse> available(@AuthenticationPrincipal Jwt jwt, @RequestParam Instant from, @RequestParam Instant to) {
        return service.available(jwt.getSubject(), from, to);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookAppointmentRequest request) {
        return service.book(jwt.getSubject(), request);
    }
    @GetMapping("/me")
    public List<AppointmentResponse> mine(@AuthenticationPrincipal Jwt jwt) { return service.mine(jwt.getSubject()); }
}
