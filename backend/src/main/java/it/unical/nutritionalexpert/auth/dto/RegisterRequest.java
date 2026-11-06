/* DTO del JSON di registrazione: normalizza i campi anagrafici e definisce la validazione; il ruolo USER viene scelto dal service. */
package it.unical.nutritionalexpert.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Pattern(
                regexp = "^$|^[+0-9][0-9 .()\\-]{5,29}$",
                message = "Phone number format is invalid")
        String phone) {
    /** Rimuove gli spazi ai margini dei campi anagrafici prima della validazione, preservando i valori null. */
    public RegisterRequest {
        email = email == null ? null : email.strip();
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
        phone = phone == null ? null : phone.strip();
    }

    /** Oscura i dati del DTO quando viene convertito in testo, evitando credenziali o dati personali nei log. */
    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
