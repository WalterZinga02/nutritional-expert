/* DTO del JSON di login: contiene email e password con vincoli verificati da @Valid nel controller. */
package it.unical.nutritionalexpert.auth.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LoginRequest(@NotBlank @Size(max=255) String email, @NotBlank @Size(max=72) String password) {
    /** Oscura i dati del DTO quando viene convertito in testo, evitando credenziali o dati personali nei log. */
    @Override
    public String toString() { return "LoginRequest[redacted]"; }
}
