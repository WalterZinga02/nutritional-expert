/* Test integrati degli slot: permessi ADMIN, date, sovrapposizioni, prenotazioni e aggiornamenti concorrenti. */
package it.unical.nutritionalexpert.availability;

import java.time.Instant;
import java.util.Map;
import java.util.List;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AvailabilityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AvailabilitySlotRepository slots;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    private String admin;
    private String user;
    private long userId;
    private Instant start;

    // Prepara utenti, token e dati iniziali isolati per il test.
    @BeforeEach void prepare() {
        clean();
        User adminUser = users.saveAndFlush(new User("admin@example.test", "unused", "Admin", "Test", null, UserRole.ADMIN));
        User ordinary = users.saveAndFlush(new User("user@example.test", "unused", "User", "Test", null, UserRole.USER));
        admin = tokens.issue(adminUser).accessToken();
        user = tokens.issue(ordinary).accessToken();
        userId = ordinary.getId();
        start = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }
    // Pulisce i dati di test rispettando le dipendenze tra tabelle.
    @AfterEach void clean() {
        jdbc.update("DELETE FROM appointment");
        slots.deleteAll();
        users.deleteAll();
    }
    // Invia una richiesta di creazione slot e verifica il codice HTTP atteso.
    private MvcResult create(Instant from, Instant to, int expected) throws Exception {
        return mvc.perform(post("/api/admin/availability").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("startTime", from, "endTime", to))))
                .andExpect(status().is(expected)).andReturn();
    }
    // Estrae l’identificativo dalla risposta JSON.
    private long id(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    // Invia una modifica ADMIN con la versione indicata per verificare stato o disponibilità.
    private MvcResult update(long id, boolean available, long version, int expected) throws Exception {
        return mvc.perform(patch("/api/admin/availability/" + id).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("available", available, "version", version))))
                .andExpect(status().is(expected)).andReturn();
    }
    // Verifica che le operazioni sugli slot siano riservate agli ADMIN.
    @Test void permissionsProtectAllOperations() throws Exception {
        for (String credential : new String[] {"", user}) {
            int expected = credential.isEmpty() ? 401 : 403;
            var list = get("/api/admin/availability").param("from", start.toString()).param("to", start.plusSeconds(3600).toString());
            var create = post("/api/admin/availability").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("startTime", start, "endTime", start.plusSeconds(1800))));
            var update = patch("/api/admin/availability/1").contentType(MediaType.APPLICATION_JSON).content("{\"available\":false,\"version\":0}");
            if (!credential.isEmpty()) {
                list.header("Authorization", "Bearer " + credential);
                create.header("Authorization", "Bearer " + credential);
                update.header("Authorization", "Bearer " + credential);
            }
            mvc.perform(list).andExpect(status().is(expected));
            mvc.perform(create).andExpect(status().is(expected));
            mvc.perform(update).andExpect(status().is(expected));
        }
        assertThat(slots.count()).isZero();
    }
    // Verifica creazione, ordinamento e ammissibilità degli slot adiacenti.
    @Test void createsListsInTimeOrderAndAllowsAdjacentSlots() throws Exception {
        create(start.plusSeconds(1800), start.plusSeconds(3600), 201);
        create(start, start.plusSeconds(1800), 201);
        mvc.perform(get("/api/admin/availability").header("Authorization", "Bearer " + admin)
                .param("from", start.toString()).param("to", start.plusSeconds(86400).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startTime").value(start.toString()))
                .andExpect(jsonPath("$[0].available").value(true)).andExpect(jsonPath("$[0].version").value(0));
        mvc.perform(get("/api/admin/availability").header("Authorization", "Bearer " + admin)
                .param("from", start.plusSeconds(3600).toString()).param("to", start.plusSeconds(7200).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    // Verifica il rifiuto di date e parametri non validi.
    @Test void rejectsInvalidTimesAndQueryParameters() throws Exception {
        create(start, start, 400);
        create(start, start.minusSeconds(1), 400);
        create(Instant.now().minusSeconds(60), start, 400);
        create(start, start.plusSeconds(86401), 400);
        mvc.perform(post("/api/admin/availability").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/availability").header("Authorization", "Bearer " + admin)
                .param("from", "invalid").param("to", start.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/availability").header("Authorization", "Bearer " + admin)
                .param("from", start.toString()).param("to", start.plusSeconds(32 * 86400L).toString()))
                .andExpect(status().isBadRequest());
        assertThat(slots.count()).isZero();
    }
    // Verifica che anche gli slot disabilitati impediscano sovrapposizioni.
    @Test void rejectsOverlapIncludingDisabledSlots() throws Exception {
        long id = id(create(start, start.plusSeconds(1800), 201));
        create(start.plusSeconds(900), start.plusSeconds(2700), 409);
        create(start.minusSeconds(900), start.plusSeconds(2700), 409);
        update(id, false, 0, 200);
        create(start, start.plusSeconds(1800), 409);
    }
    // Verifica incremento di versione e rifiuto delle richieste obsolete.
    @Test void updatesVersionAndRejectsStaleRequests() throws Exception {
        long id = id(create(start, start.plusSeconds(1800), 201));
        MvcResult disabled = update(id, false, 0, 200);
        assertThat(json.readTree(disabled.getResponse().getContentAsString()).get("version").asLong()).isEqualTo(1);
        update(id, true, 0, 409);
        assertThat(slots.findById(id).orElseThrow().isAvailable()).isFalse();
        update(id, true, 1, 200);
        assertThat(slots.findById(id).orElseThrow().getVersion()).isEqualTo(2);
        update(Long.MAX_VALUE, false, 0, 404);
        mvc.perform(patch("/api/admin/availability/" + id).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isBadRequest());
    }
    // Verifica i limiti di modifica degli slot in base a orario e prenotazioni.
    @Test void refusesPastOrBookedSlotsAndAllowsCancelledBooking() throws Exception {
        AvailabilitySlot past = slots.saveAndFlush(new AvailabilitySlot(Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800)));
        update(past.getId(), false, 0, 409);
        long id = id(create(start, start.plusSeconds(1800), 201));
        jdbc.update("INSERT INTO appointment (user_id, slot_id, type, status, version, created_at, updated_at) VALUES (?, ?, 'DIET_REVIEW', 'CONFIRMED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", userId, id);
        update(id, false, 0, 409);
        mvc.perform(get("/api/admin/availability").header("Authorization", "Bearer " + admin)
                .param("from", start.toString()).param("to", start.plusSeconds(3600).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].booked").value(true));
        jdbc.update("UPDATE appointment SET status = 'CANCELLED' WHERE slot_id = ?", id);
        update(id, false, 0, 200);
    }
    // Verifica che due modifiche dello stesso slot non si sovrascrivano.
    @Test void simultaneousUpdatesDoNotOverwriteEachOther() throws Exception {
        long id = id(create(start, start.plusSeconds(1800), 201));
        assertConcurrent(() -> mvc.perform(patch("/api/admin/availability/" + id).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false,\"version\":0}"))
                .andReturn().getResponse().getStatus(), 200);
        assertThat(slots.findById(id).orElseThrow().getVersion()).isEqualTo(1);
    }
    // Verifica su PostgreSQL il vincolo anti-sovrapposizione anche tra inserimenti concorrenti.
    @Test void postgresPreventsOverlapsBetweenConcurrentCreates() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            Assumptions.assumeTrue(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
        }
        var sequence = new java.util.concurrent.atomic.AtomicInteger();
        assertConcurrent(() -> {
            int offset = sequence.getAndIncrement() * 600;
            return mvc.perform(post("/api/admin/availability").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                            "startTime", start.plusSeconds(offset), "endTime", start.plusSeconds(offset + 1800)))))
                    .andReturn().getResponse().getStatus();
        }, 201);
        assertThat(slots.count()).isEqualTo(1);
    }
    // Avvia operazioni simultanee e verifica un successo e un conflitto.
    private void assertConcurrent(Callable<Integer> operation, int success) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
            return operation.call();
        };
        try {
            Future<Integer> first = executor.submit(task), second = executor.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(success, 409);
        } finally { go.countDown(); executor.shutdownNow(); }
    }
}
