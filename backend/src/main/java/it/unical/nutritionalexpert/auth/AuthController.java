/* API /api/auth: Spring converte il JSON in DTO e applica @Valid; il controller delega la logica ad AuthService. */
package it.unical.nutritionalexpert.auth;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import it.unical.nutritionalexpert.auth.dto.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AuthController(AuthService auth) { this.auth = auth; }
    /** Riceve il DTO validato di registrazione e restituisce account e token con HTTP 201. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }
    /** Riceve le credenziali e restituisce il risultato del login. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
    /** Legge il profilo dell’utente identificato dal JWT verificato da Spring Security. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) { return auth.currentUser(jwt.getSubject()); }

    /** Inoltra al service i campi modificabili del profilo e l’identità del token. */
    @PatchMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(jwt.getSubject(), request);
    }
}
