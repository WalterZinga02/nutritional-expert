package it.unical.nutritionalexpert.availability;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    boolean existsByStartTimeLessThanAndEndTimeGreaterThan(Instant end, Instant start);
    List<AvailabilitySlot> findByStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(Instant to, Instant from);
}
