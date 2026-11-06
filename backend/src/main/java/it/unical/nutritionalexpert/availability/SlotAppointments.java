package it.unical.nutritionalexpert.availability;

import java.time.Instant;
import java.util.Set;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Read-only queries shared by the ADMIN agenda and user availability search.
@Repository
public class SlotAppointments {
    private final JdbcTemplate jdbc;
    public SlotAppointments(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean isBooked(long slotId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointment WHERE slot_id = ? AND status <> 'CANCELLED'",
                Long.class, slotId) > 0;
    }
    public Set<Long> bookedInRange(Instant from, Instant to) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT a.slot_id FROM appointment a JOIN availability_slot s ON s.id = a.slot_id
                WHERE a.status <> 'CANCELLED' AND s.start_time < ? AND s.end_time > ?
                """, Long.class, java.sql.Timestamp.from(to), java.sql.Timestamp.from(from)));
    }
}
