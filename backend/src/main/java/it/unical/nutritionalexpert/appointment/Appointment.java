/* Entità JPA della tabella appointment: collega utente e slot, conserva tipo, stato e storico. @Version protegge gli aggiornamenti concorrenti. */
package it.unical.nutritionalexpert.appointment;

import java.time.Instant;
import jakarta.persistence.*;
import it.unical.nutritionalexpert.user.User;
import it.unical.nutritionalexpert.availability.AvailabilitySlot;

@Entity
@Table(name = "appointment")
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private AvailabilitySlot slot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private AppointmentType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.REQUESTED;
    @Column(length = 1000)
    private String notes;
    // JPA include la versione nell’UPDATE: una scrittura concorrente causa un conflitto invece di sovrascrivere i dati.
    @Version private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Costruttore vuoto richiesto da JPA per ricostruire l’entità dal database. */
    protected Appointment() {}
    /** Costruisce l’oggetto con i valori ricevuti. */
    public Appointment(User user, AvailabilitySlot slot, AppointmentType type) {
        this.user = user;
        this.slot = slot;
        this.type = type;
    }
    /** Callback JPA: inizializza le date prima del primo inserimento. */
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    /** Callback JPA: aggiorna la data di modifica prima dell’UPDATE. */
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    /** Restituisce l’identificativo assegnato dal database. */
    public Long getId() { return id; }
    /** Restituisce l’entità utente proprietaria della visita. */
    public User getUser() { return user; }
    /** Assegna lo stato in memoria; le transizioni ammesse sono controllate dai service prima della persistenza. */
    public void changeStatus(AppointmentStatus status) { this.status = status; }
    /** Restituisce l’entità slot collegata alla visita. */
    public AvailabilitySlot getSlot() { return slot; }
    /** Restituisce il tipo di visita. */
    public AppointmentType getType() { return type; }
    /** Restituisce lo stato corrente della visita. */
    public AppointmentStatus getStatus() { return status; }
    /** Restituisce la data di creazione del record. */
    public Instant getCreatedAt() { return createdAt; }
    /** Restituisce la versione usata per rilevare aggiornamenti concorrenti. */
    public long getVersion() { return version; }
}
