package it.unical.nutritionalexpert.appointment;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @EntityGraph(attributePaths = "slot")
    List<Appointment> findByUserIdOrderBySlotStartTimeDescIdDesc(Long userId);
    boolean existsBySlotIdAndStatusNot(Long slotId, AppointmentStatus status);
}
