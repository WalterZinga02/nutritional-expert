package it.unical.nutritionalexpert.auth.dto;
import it.unical.nutritionalexpert.user.*;
public record UserResponse(Long id, String email, String firstName, String lastName, String phone, UserRole role) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(), u.getRole());
    }
}
