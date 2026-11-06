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
    @Version private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {}
    public Appointment(User user, AvailabilitySlot slot, AppointmentType type) {
        this.user = user;
        this.slot = slot;
        this.type = type;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void changeStatus(AppointmentStatus status) { this.status = status; }
    public AvailabilitySlot getSlot() { return slot; }
    public AppointmentType getType() { return type; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
