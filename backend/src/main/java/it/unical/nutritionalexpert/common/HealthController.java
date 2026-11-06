/* Endpoint pubblico per verificare che il backend risponda, senza richiedere autenticazione. */
package it.unical.nutritionalexpert.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class HealthController {

    /** Risponde con stato UP, nome applicazione e ora; non esegue una diagnosi del database. */
    @GetMapping("/health")
    Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "application", "nutritional-expert-backend",
                "timestamp", Instant.now().toString());
    }
}

