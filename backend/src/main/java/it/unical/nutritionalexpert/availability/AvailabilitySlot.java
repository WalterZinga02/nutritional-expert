/* Entità JPA della tabella availability_slot: rappresenta un intervallo dello studio e la sua disponibilità, con versione concorrente. */
package it.unical.nutritionalexpert.availability;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "availability_slot")
public class AvailabilitySlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "start_time", nullable = false, unique = true)
    private Instant startTime;
    @Column(name = "end_time", nullable = false)
    private Instant endTime;
    @Column(nullable = false)
    private boolean available = true;
    // JPA include la versione nell’UPDATE: una scrittura concorrente causa un conflitto invece di sovrascrivere i dati.
    @Version
    private long version;

    /** Costruttore vuoto richiesto da JPA per ricostruire l’entità dal database. */
    protected AvailabilitySlot() {}
    /** Costruisce l’oggetto con i valori ricevuti. */
    public AvailabilitySlot(Instant startTime, Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
    /** Restituisce l’identificativo assegnato dal database. */
    public Long getId() { return id; }
    /** Restituisce l’istante di inizio dello slot. */
    public Instant getStartTime() { return startTime; }
    /** Restituisce l’istante di fine dello slot. */
    public Instant getEndTime() { return endTime; }
    /** Restituisce il flag di disponibilità; la presenza di prenotazioni viene verificata separatamente. */
    public boolean isAvailable() { return available; }
    /** Restituisce la versione usata per rilevare aggiornamenti concorrenti. */
    public long getVersion() { return version; }
    /** Cambia il flag in memoria; il service controlla le regole e JPA persiste la modifica. */
    public void setAvailable(boolean available) { this.available = available; }
}
