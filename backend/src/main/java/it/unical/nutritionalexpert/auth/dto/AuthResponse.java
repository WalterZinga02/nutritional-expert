package it.unical.nutritionalexpert.auth.dto;
import java.time.Instant;
public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    @Override
    public String toString() { return "AuthResponse[redacted]"; }
}
