/* Accesso JPA agli utenti. Spring Data implementa le query dai nomi dei metodi e restituisce entità User, non DTO. */
package it.unical.nutritionalexpert.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Cerca l’utente per email ignorando maiuscole e minuscole. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Controlla preventivamente i duplicati; il vincolo DB protegge anche le registrazioni simultanee. */
    boolean existsByEmailIgnoreCase(String email);
}
