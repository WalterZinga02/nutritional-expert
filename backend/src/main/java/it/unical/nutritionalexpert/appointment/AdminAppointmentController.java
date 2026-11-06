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
    public AdminAppointmentController(AdminAppointmentService service) { this.service = service; }
    @GetMapping
    public List<AdminAppointmentResponse> list(@RequestParam Instant from, @RequestParam Instant to,
                                              @RequestParam(required = false) AppointmentStatus status) {
        return service.list(from, to, status);
    }
    @PatchMapping("/{id}/status")
    public AdminAppointmentResponse update(@PathVariable long id, @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return service.update(id, request);
    }
}
