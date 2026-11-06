/* Logica di registrazione, login e profilo. Coordina utenti, verifica delle password e rilascio del JWT nelle transazioni. */
package it.unical.nutritionalexpert.auth;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import it.unical.nutritionalexpert.auth.dto.*;
import it.unical.nutritionalexpert.user.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyHash;
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }
    /** Normalizza l’email, evita duplicati, salva la password come hash e crea un account USER con token. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esiste già un account con questa email.");
        User user = new User(email, passwords.encode(request.password()), request.firstName().strip(),
                request.lastName().strip(), request.phone() == null ? null : request.phone().strip(), UserRole.USER);
        users.saveAndFlush(user);
        return tokens.issue(user);
    }
    /** Verifica la password anche con hash fittizio per email sconosciute; restituisce JWT o errore uniforme 401. */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().strip()).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (!matches || user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o password non corrette.");
        return tokens.issue(user);
    }
    /** Carica l’utente autenticato e lo converte nel DTO pubblico. */
    @Transactional(readOnly = true)
    public UserResponse currentUser(String subject) {
        return UserResponse.from(requireCurrentUser(subject));
    }

    /** Modifica l’entità gestita da JPA, sincronizza il database e restituisce il profilo aggiornato senza rinnovare il JWT. */
    @Transactional
    public UserResponse updateProfile(String subject, UpdateProfileRequest request) {
        User user = requireCurrentUser(subject);
        user.updateProfile(request.firstName(), request.lastName(), request.phone());
        // JPA tracks the managed entity and persists changes within this transaction.
        users.flush();
        return UserResponse.from(user);
    }

    /** Converte il subject del JWT in ID e verifica che l’account esista ancora, altrimenti risponde 401. */
    private User requireCurrentUser(String subject) {
        try {
            return users.findById(Long.parseLong(subject))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non valida."));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non valida.");
        }
    }
}
