/* DTO del profilo restituito ad Angular: espone i dati utili senza includere la password hash. */
package it.unical.nutritionalexpert.auth.dto;
import it.unical.nutritionalexpert.user.*;
public record UserResponse(Long id, String email, String firstName, String lastName, String phone, UserRole role) {
    /** Converte l’entità JPA nel DTO di risposta, selezionando solo i campi da esporre. */
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(), u.getRole());
    }
}
