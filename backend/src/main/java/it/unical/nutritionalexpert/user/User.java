/* Entità JPA della tabella app_user: conserva credenziali con password hash e dati del profilo. Non viene esposta direttamente nelle API. */
package it.unical.nutritionalexpert.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Costruttore vuoto richiesto da JPA per ricostruire l’entità dal database. */
    protected User() {
        // Required by JPA.
    }

    /** Costruisce l’oggetto con i valori ricevuti. */
    public User(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String phone,
            UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.role = role;
    }

    /** Callback JPA: inizializza le date prima del primo inserimento. */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /** Callback JPA: aggiorna la data di modifica prima dell’UPDATE. */
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Aggiorna solo i campi presenti; un telefono vuoto cancella il numero salvato. */
    public void updateProfile(String firstName, String lastName, String phone) {
        if (firstName != null) this.firstName = firstName;
        if (lastName != null) this.lastName = lastName;
        if (phone != null) this.phone = phone.isEmpty() ? null : phone;
    }

    /** Restituisce l’identificativo assegnato dal database. */
    public Long getId() {
        return id;
    }

    /** Restituisce l’email dell’account. */
    public String getEmail() {
        return email;
    }

    /** Restituisce l’hash per verificare il login; non deve essere incluso nei DTO pubblici. */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** Restituisce il nome del profilo. */
    public String getFirstName() {
        return firstName;
    }

    /** Restituisce il cognome del profilo. */
    public String getLastName() {
        return lastName;
    }

    /** Restituisce il telefono, oppure null se assente. */
    public String getPhone() {
        return phone;
    }

    /** Restituisce il ruolo usato per autorizzazioni e claim JWT. */
    public UserRole getRole() {
        return role;
    }

    /** Restituisce la data di creazione del record. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Restituisce la data dell’ultima modifica del record. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
