/* Test integrati delle prenotazioni utente: proprietà, disponibilità e contesa dello slot tra utenti o con l’ADMIN. */
package it.unical.nutritionalexpert.appointment;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import it.unical.nutritionalexpert.auth.TokenService;
import it.unical.nutritionalexpert.user.*;
import it.unical.nutritionalexpert.availability.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppointmentRepository appointments;
    @Autowired AvailabilitySlotRepository slots;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    private String firstToken, secondToken, adminToken;
    private User first, second;
    private Instant start;
    private AvailabilitySlot slot;

    // Prepara utenti, token e dati iniziali isolati per il test.
    @BeforeEach void prepare() {
        clean();
        first = users.saveAndFlush(new User("first@example.test", "unused", "First", "Test", null, UserRole.USER));
        second = users.saveAndFlush(new User("second@example.test", "unused", "Second", "Test", null, UserRole.USER));
        User admin = users.saveAndFlush(new User("admin@example.test", "unused", "Admin", "Test", null, UserRole.ADMIN));
        firstToken = tokens.issue(first).accessToken();
        secondToken = tokens.issue(second).accessToken();
        adminToken = tokens.issue(admin).accessToken();
        start = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        slot = slots.saveAndFlush(new AvailabilitySlot(start, start.plusSeconds(1800)));
    }
    // Pulisce i dati di test rispettando le dipendenze tra tabelle.
    @AfterEach void clean() { appointments.deleteAll(); slots.deleteAll(); users.deleteAll(); }
    // Invia una prenotazione di prova con token, slot e versione indicati.
    private MvcResult book(String token, long id, long version, String type) throws Exception {
        return mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "slotId", id, "version", version, "type", type, "userId", second.getId(), "status", "CONFIRMED"))))
                .andReturn();
    }
    // Verifica che prenotazione e storico usino il proprietario del JWT.
    @Test void booksForTokenOwnerAndReturnsOnlyTheirAppointments() throws Exception {
        MvcResult result = book(firstToken, slot.getId(), 0, "DIET_REVIEW");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var response = json.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("status").asString()).isEqualTo("REQUESTED");
        assertThat(response.has("user")).isFalse();
        assertThat(response.has("passwordHash")).isFalse();
        AvailabilitySlot stored = slots.findById(slot.getId()).orElseThrow();
        assertThat(stored.isAvailable()).isFalse();
        assertThat(stored.getVersion()).isEqualTo(1);
        mvc.perform(get("/api/appointments/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("DIET_REVIEW"));
        mvc.perform(get("/api/appointments/me").header("Authorization", "Bearer " + secondToken).param("userId", first.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(book(firstToken, slot.getId(), 0, "DIET_REVIEW").getResponse().getStatus()).isEqualTo(409);
        assertThat(appointments.count()).isEqualTo(1);
    }
    // Verifica che la ricerca escluda slot passati, disabilitati o occupati.
    @Test void availableListOmitsPastDisabledAndBookedSlots() throws Exception {
        slots.saveAndFlush(new AvailabilitySlot(Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800)));
        AvailabilitySlot disabled = new AvailabilitySlot(start.plusSeconds(3600), start.plusSeconds(5400));
        disabled.setAvailable(false);
        slots.saveAndFlush(disabled);
        AvailabilitySlot booked = slots.saveAndFlush(new AvailabilitySlot(start.plusSeconds(7200), start.plusSeconds(9000)));
        // Even an inconsistent available flag must never expose an occupied slot.
        appointments.saveAndFlush(new Appointment(first, booked, AppointmentType.DIET_CREATION));
        mvc.perform(get("/api/appointments/available-slots").header("Authorization", "Bearer " + firstToken)
                .param("from", Instant.now().minusSeconds(7200).toString()).param("to", start.plusSeconds(10000).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(slot.getId()));
    }
    // Verifica che non si possano prenotare slot non più validi o con versione obsoleta.
    @Test void refusesUnavailablePastMissingAndStaleSlots() throws Exception {
        assertThat(book(firstToken, slot.getId(), 8, "DIET_REVIEW").getResponse().getStatus()).isEqualTo(409);
        slot.setAvailable(false);
        slots.saveAndFlush(slot);
        assertThat(book(firstToken, slot.getId(), 1, "DIET_REVIEW").getResponse().getStatus()).isEqualTo(409);
        AvailabilitySlot past = slots.saveAndFlush(new AvailabilitySlot(Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800)));
        assertThat(book(firstToken, past.getId(), 0, "DIET_REVIEW").getResponse().getStatus()).isEqualTo(409);
        assertThat(book(firstToken, Long.MAX_VALUE, 0, "DIET_REVIEW").getResponse().getStatus()).isEqualTo(404);
        assertThat(appointments.count()).isZero();
    }
    // Verifica payload non validi e prenotazioni senza autenticazione.
    @Test void rejectsInvalidPayloadAndUnauthenticatedRequests() throws Exception {
        for (String payload : List.of("{}", "{\"slotId\":-1,\"version\":-1,\"type\":\"DIET_REVIEW\"}",
                "{\"slotId\":1,\"version\":0,\"type\":\"INVALID\"}")) {
            mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + firstToken)
                    .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/available-slots").param("from", start.toString())
                .param("to", start.plusSeconds(3600).toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/available-slots").header("Authorization", "Bearer " + firstToken)
                .param("from", start.toString()).param("to", start.minusSeconds(1).toString())).andExpect(status().isBadRequest());
        assertThat(slots.findById(slot.getId()).orElseThrow().isAvailable()).isTrue();
        assertThat(appointments.count()).isZero();
    }
    // Verifica che un account eliminato non acceda alle prenotazioni.
    @Test void deletedAccountCannotBookOrReadAppointments() throws Exception {
        users.deleteById(first.getId());
        assertThat(book(firstToken, slot.getId(), 0, "DIET_REVIEW").getResponse().getStatus()).isEqualTo(401);
        mvc.perform(get("/api/appointments/me").header("Authorization", "Bearer " + firstToken)).andExpect(status().isUnauthorized());
        assertThat(slots.findById(slot.getId()).orElseThrow().isAvailable()).isTrue();
    }
    // Verifica che solo una delle prenotazioni concorrenti dello stesso slot riesca.
    @Test void simultaneousBookingsHaveOnlyOneWinner() throws Exception {
        List<Integer> result = race(
                () -> book(firstToken, slot.getId(), 0, "DIET_CREATION").getResponse().getStatus(),
                () -> book(secondToken, slot.getId(), 0, "NUTRITIONAL_CONSULTATION").getResponse().getStatus());
        assertThat(result).containsExactlyInAnyOrder(201, 409);
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(slots.findById(slot.getId()).orElseThrow().getVersion()).isEqualTo(1);
    }
    // Verifica il conflitto tra prenotazione utente e disabilitazione ADMIN dello stesso slot.
    @Test void bookingConflictsWithAdminDisablingTheSameSlot() throws Exception {
        List<Integer> result = race(
                () -> book(firstToken, slot.getId(), 0, "WEIGHING_AND_BMI").getResponse().getStatus(),
                () -> mvc.perform(patch("/api/admin/availability/" + slot.getId()).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false,\"version\":0}"))
                        .andReturn().getResponse().getStatus());
        assertThat(result.get(0) == 201 && result.get(1) == 409 || result.get(0) == 409 && result.get(1) == 200).isTrue();
        assertThat(appointments.count()).isEqualTo(result.get(0) == 201 ? 1 : 0);
        assertThat(slots.findById(slot.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(slots.findById(slot.getId()).orElseThrow().getVersion()).isEqualTo(1);
    }
    // Verifica che lo storico annullato non impedisca di prenotare uno slot riaperto.
    @Test void cancelledAppointmentDoesNotPreventRebookingAnAvailableSlot() throws Exception {
        appointments.saveAndFlush(new Appointment(first, slot, AppointmentType.DIET_REVIEW));
        jdbc.update("UPDATE appointment SET status = 'CANCELLED' WHERE slot_id = ?", slot.getId());
        assertThat(book(secondToken, slot.getId(), 0, "DIET_CREATION").getResponse().getStatus()).isEqualTo(201);
        assertThat(appointments.count()).isEqualTo(2);
    }
    // Sincronizza due operazioni su thread separati e raccoglie i codici HTTP risultanti.
    private List<Integer> race(Callable<Integer> a, Callable<Integer> b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        java.util.function.Function<Callable<Integer>, Callable<Integer>> wrap = work -> () -> {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
            return work.call();
        };
        try {
            Future<Integer> first = executor.submit(wrap.apply(a)), second = executor.submit(wrap.apply(b));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally { go.countDown(); executor.shutdownNow(); }
    }
}
