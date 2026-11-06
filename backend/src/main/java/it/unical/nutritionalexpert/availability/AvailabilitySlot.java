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
    @Version
    private long version;

    protected AvailabilitySlot() {}
    public AvailabilitySlot(Instant startTime, Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
    public Long getId() { return id; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public boolean isAvailable() { return available; }
    public long getVersion() { return version; }
    public void setAvailable(boolean available) { this.available = available; }
}
