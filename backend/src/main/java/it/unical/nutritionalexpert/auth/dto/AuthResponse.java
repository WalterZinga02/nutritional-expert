/* DTO di risposta a login/registrazione: contiene JWT, tipo Bearer, scadenza e profilo; non è una tabella del database. */
package it.unical.nutritionalexpert.auth.dto;
import java.time.Instant;
public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    /** Oscura i dati del DTO quando viene convertito in testo, evitando credenziali o dati personali nei log. */
    @Override
    public String toString() { return "AuthResponse[redacted]"; }
}
