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
class AdminAppointmentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppointmentRepository appointments;
    @Autowired AvailabilitySlotRepository slots;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    private String adminToken, userToken;
    private User customer;
    private Instant start;
    private Appointment appointment;

    @BeforeEach void prepare() {
        clean();
        customer = users.saveAndFlush(new User("customer@example.test", "unused", "Mario", "Rossi", "3331234567", UserRole.USER));
        User admin = users.saveAndFlush(new User("admin@example.test", "unused", "Admin", "Test", null, UserRole.ADMIN));
        userToken = tokens.issue(customer).accessToken();
        adminToken = tokens.issue(admin).accessToken();
        start = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        appointment = create(start, AppointmentStatus.REQUESTED);
    }
    private Appointment create(Instant time, AppointmentStatus status) {
        AvailabilitySlot slot = new AvailabilitySlot(time, time.plusSeconds(1800));
        slot.setAvailable(false);
        slots.saveAndFlush(slot);
        Appointment result = new Appointment(customer, slot, AppointmentType.DIET_REVIEW);
        result.changeStatus(status);
        return appointments.saveAndFlush(result);
    }
    @AfterEach void clean() { appointments.deleteAll(); slots.deleteAll(); users.deleteAll(); }
    private MvcResult update(long id, String status, long version) throws Exception {
        return mvc.perform(patch("/api/admin/appointments/" + id + "/status").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("status", status, "version", version))))
                .andReturn();
    }
    @Test void adminListsCustomersFiltersStatusAndBoundsDates() throws Exception {
        create(start.plusSeconds(3600), AppointmentStatus.CONFIRMED);
        create(start.plusSeconds(86400), AppointmentStatus.REQUESTED);
        mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer " + adminToken)
                .param("from", start.toString()).param("to", start.plusSeconds(7200).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].customer.firstName").value("Mario"))
                .andExpect(jsonPath("$[0].customer.email").value("customer@example.test"))
                .andExpect(jsonPath("$[0].customer.passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].customer.role").doesNotExist());
        mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer " + adminToken)
                .param("from", start.toString()).param("to", start.plusSeconds(7200).toString()).param("status", "CONFIRMED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer " + adminToken)
                .param("from", start.toString()).param("to", start.plusSeconds(32 * 86400L).toString()))
                .andExpect(status().isBadRequest());
    }
    @Test void onlyAdminCanReadOrChangeStatus() throws Exception {
        for (String token : List.of("", userToken)) {
            int expected = token.isEmpty() ? 401 : 403;
            var read = get("/api/admin/appointments").param("from", start.toString()).param("to", start.plusSeconds(3600).toString());
            var write = patch("/api/admin/appointments/" + appointment.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\",\"version\":0}");
            if (!token.isEmpty()) { read.header("Authorization", "Bearer " + token); write.header("Authorization", "Bearer " + token); }
            mvc.perform(read).andExpect(status().is(expected));
            mvc.perform(write).andExpect(status().is(expected));
        }
        assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
    }
    @Test void confirmsFutureRequestAndUserSeesUpdatedStatus() throws Exception {
        var result = update(appointment.getId(), "CONFIRMED", 0);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("version").asLong()).isEqualTo(1);
        mvc.perform(get("/api/appointments/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        assertThat(slots.findById(appointment.getSlot().getId()).orElseThrow().isAvailable()).isFalse();
    }
    @Test void completionRequiresConfirmedAppointmentWhoseEndHasPassed() throws Exception {
        assertThat(update(appointment.getId(), "COMPLETED", 0).getResponse().getStatus()).isEqualTo(409);
        update(appointment.getId(), "CONFIRMED", 0);
        assertThat(update(appointment.getId(), "COMPLETED", 1).getResponse().getStatus()).isEqualTo(409);
        Appointment past = create(Instant.now().minusSeconds(7200), AppointmentStatus.CONFIRMED);
        assertThat(update(past.getId(), "COMPLETED", 0).getResponse().getStatus()).isEqualTo(200);
        assertThat(update(past.getId(), "CANCELLED", 1).getResponse().getStatus()).isEqualTo(409);
        assertThat(update(past.getId(), "REQUESTED", 1).getResponse().getStatus()).isEqualTo(409);
        Appointment expiredRequest = create(Instant.now().minusSeconds(14400), AppointmentStatus.REQUESTED);
        assertThat(update(expiredRequest.getId(), "CONFIRMED", 0).getResponse().getStatus()).isEqualTo(409);
    }
    @Test void cancellationKeepsRecordAndSlotDisabledUntilExplicitReopening() throws Exception {
        assertThat(update(appointment.getId(), "CANCELLED", 0).getResponse().getStatus()).isEqualTo(200);
        assertThat(appointments.count()).isEqualTo(1);
        AvailabilitySlot slot = slots.findById(appointment.getSlot().getId()).orElseThrow();
        assertThat(slot.isAvailable()).isFalse();
        assertThat(update(appointment.getId(), "CONFIRMED", 1).getResponse().getStatus()).isEqualTo(409);
        mvc.perform(patch("/api/admin/availability/" + slot.getId()).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("available", true, "version", slot.getVersion()))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "slotId", slot.getId(), "version", slot.getVersion() + 1, "type", "DIET_CREATION"))))
                .andExpect(status().isCreated());
        assertThat(appointments.count()).isEqualTo(2);
        assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }
    @Test void cancelsConfirmedAppointmentAndRejectsStaleOrInvalidRequests() throws Exception {
        update(appointment.getId(), "CONFIRMED", 0);
        assertThat(update(appointment.getId(), "CANCELLED", 0).getResponse().getStatus()).isEqualTo(409);
        assertThat(update(appointment.getId(), "CANCELLED", 1).getResponse().getStatus()).isEqualTo(200);
        assertThat(update(Long.MAX_VALUE, "CONFIRMED", 0).getResponse().getStatus()).isEqualTo(404);
        for (String payload : List.of("{}", "{\"status\":\"INVALID\",\"version\":0}", "{\"status\":\"CONFIRMED\",\"version\":-1}")) {
            mvc.perform(patch("/api/admin/appointments/" + appointment.getId() + "/status")
                    .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
    }
    @Test void concurrentStatusChangesHaveOnlyOneWinner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String status : List.of("CONFIRMED", "CANCELLED")) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
                    return update(appointment.getId(), status, 0).getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(appointments.findById(appointment.getId()).orElseThrow().getVersion()).isEqualTo(1);
            assertThat(slots.findById(appointment.getSlot().getId()).orElseThrow().isAvailable()).isFalse();
        } finally { go.countDown(); executor.shutdownNow(); }
    }
}
