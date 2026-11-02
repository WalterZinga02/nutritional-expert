package it.unical.nutritionalexpert.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Omitted/null fields remain unchanged; an empty phone removes the existing number.
public record UpdateProfileRequest(
        @Size(min = 1, max = 100, message = "Il nome deve contenere da 1 a 100 caratteri.")
        @Pattern(regexp = "(?s).*\\S.*", message = "Inserisci il nome.") String firstName,
        @Size(min = 1, max = 100, message = "Il cognome deve contenere da 1 a 100 caratteri.")
        @Pattern(regexp = "(?s).*\\S.*", message = "Inserisci il cognome.") String lastName,
        @Pattern(regexp = "^$|^[+0-9][0-9 .()\\-]{5,29}$", message = "Inserisci un numero di telefono valido.")
        String phone) {
    public UpdateProfileRequest {
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
        phone = phone == null ? null : phone.strip();
    }

    @Override
    public String toString() { return "UpdateProfileRequest[redacted]"; }
}
