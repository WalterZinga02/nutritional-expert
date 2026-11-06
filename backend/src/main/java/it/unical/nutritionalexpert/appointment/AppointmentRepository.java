/* Repository JPA degli appuntamenti: restituisce entità e applica filtri per proprietario, slot o agenda ADMIN. */
package it.unical.nutritionalexpert.appointment;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    /** Cerca per ID e proprietario insieme, evitando di esporre visite altrui; carica anche lo slot. */
    @EntityGraph(attributePaths = "slot")
    java.util.Optional<Appointment> findByIdAndUserId(Long id, Long userId);
    /** Legge lo storico personale con slot già caricati, dalle visite più recenti. */
    @EntityGraph(attributePaths = "slot")
    List<Appointment> findByUserIdOrderBySlotStartTimeDescIdDesc(Long userId);
    /** Verifica se lo slot ha una visita con stato diverso da quello escluso. */
    boolean existsBySlotIdAndStatusNot(Long slotId, AppointmentStatus status);

    /** Legge le visite nell’intervallo con filtro stato opzionale, caricando cliente e slot per il DTO ADMIN. */
    @EntityGraph(attributePaths = {"slot", "user"})
    @Query("select a from Appointment a where a.slot.startTime < :to and a.slot.endTime > :from " +
            "and (:status is null or a.status = :status) order by a.slot.startTime, a.id")
    List<Appointment> findForAdmin(Instant from, Instant to, AppointmentStatus status);
}
