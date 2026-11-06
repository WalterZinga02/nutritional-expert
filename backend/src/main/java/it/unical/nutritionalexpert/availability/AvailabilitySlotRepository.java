/* Repository JPA degli slot: legge e salva entità AvailabilitySlot e cerca intervalli sovrapposti. */
package it.unical.nutritionalexpert.availability;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    /** Verifica sovrapposizioni anche con slot disabilitati; intervalli adiacenti sono consentiti. */
    boolean existsByStartTimeLessThanAndEndTimeGreaterThan(Instant end, Instant start);
    /** Legge gli slot che intersecano l’intervallo richiesto, ordinati per inizio. */
    List<AvailabilitySlot> findByStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(Instant to, Instant from);
}
