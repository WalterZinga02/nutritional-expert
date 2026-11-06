/* Genera JWT firmati con ID utente, ruolo e scadenza. Il token viene restituito al frontend insieme al profilo pubblico. */
package it.unical.nutritionalexpert.auth;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import it.unical.nutritionalexpert.auth.dto.*;
import it.unical.nutritionalexpert.user.User;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;
    /** Riceve da Spring le dipendenze necessarie, senza crearle manualmente. */
    public TokenService(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer,
                        @Value("${app.jwt.ttl}") Duration ttl) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }
    /** Firma un JWT HS256 con ID, ruolo e scadenza configurata e prepara AuthResponse. */
    public AuthResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiresAt, UserResponse.from(user));
    }
}
