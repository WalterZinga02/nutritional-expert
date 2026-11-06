package it.unical.nutritionalexpert.appointment;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @EntityGraph(attributePaths = "slot")
    List<Appointment> findByUserIdOrderBySlotStartTimeDescIdDesc(Long userId);
    boolean existsBySlotIdAndStatusNot(Long slotId, AppointmentStatus status);

    @EntityGraph(attributePaths = {"slot", "user"})
    @Query("select a from Appointment a where a.slot.startTime < :to and a.slot.endTime > :from " +
            "and (:status is null or a.status = :status) order by a.slot.startTime, a.id")
    List<Appointment> findForAdmin(Instant from, Instant to, AppointmentStatus status);
}
