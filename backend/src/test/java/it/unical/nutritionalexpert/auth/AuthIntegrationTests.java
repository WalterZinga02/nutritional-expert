package it.unical.nutritionalexpert.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import it.unical.nutritionalexpert.user.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;

    @BeforeEach void cleanUsers() { users.deleteAll(); }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "Password123!",
                        "firstName", "Mario", "lastName", "Rossi", "phone", "", "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
    }

    private String token(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    @Test void registrationHashesPasswordAndTokenIdentifiesUser() throws Exception {
        String token = token(register("Mario@Example.it"));
        User user = users.findByEmailIgnoreCase("mario@example.it").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("mario@example.it");
        assertThat(user.getPasswordHash()).isNotEqualTo("Password123!");
        assertThat(passwords.matches("Password123!", user.getPasswordHash())).isTrue();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("mario@example.it"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/example").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test void loginIgnoresEmailCaseAndWhitespace() throws Exception {
        register("mario@example.it");
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", " MARIO@EXAMPLE.IT ", "password", "Password123!"))))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token(result)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Mario"));
    }

    @Test void duplicateEmailHasConflictResponse() throws Exception {
        register("mario@example.it");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "MARIO@example.it", "password", "Password123!",
                        "firstName", "Altro", "lastName", "Utente"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test void invalidInputsAreRejectedWithoutCreatingUser() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "invalid", "password", "short",
                        "firstName", " ", "lastName", "", "phone", "abc"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        assertThat(users.count()).isZero();
    }

    @Test void concurrentRegistrationsCannotCreateDuplicateAccounts() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
            for (String email : new String[] {"race@example.it", "RACE@example.it"}) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "Password123!",
                                    "firstName", "Mario", "lastName", "Rossi"))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(results.get(0).get(10, TimeUnit.SECONDS),
                    results.get(1).get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertThat(users.count()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test void wrongPasswordAndUnknownEmailHaveSameError() throws Exception {
        register("mario@example.it");
        for (String email : new String[] {"mario@example.it", "unknown@example.it"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", "WrongPassword!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Email o password non corrette."));
        }
    }

    @Test void missingMalformedTamperedExpiredAndWrongIssuerTokensAreRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        String valid = token(register("mario@example.it"));
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        for (String token : new String[] {"not-a-token", tampered,
                signedToken("nutritional-expert", Instant.now().minusSeconds(120)),
                signedToken("other-app", Instant.now().plusSeconds(300))}) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").exists());
        }
    }

    @Test void deletedAccountCannotRetrieveProfile() throws Exception {
        String token = token(register("mario@example.it"));
        users.deleteAll();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void profileUpdatePersistsOnlyAllowedFieldsForAuthenticatedUser() throws Exception {
        String token = token(register("owner@example.it"));
        register("other@example.it");
        User original = users.findByEmailIgnoreCase("owner@example.it").orElseThrow();
        User other = users.findByEmailIgnoreCase("other@example.it").orElseThrow();
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "firstName", "  Anna  ", "lastName", "  Verdi ", "phone", " +39 3331234567 ",
                        "id", other.getId(), "email", "changed@example.it", "role", "ADMIN", "password", "Changed123!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.phone").value("+39 3331234567"))
                .andExpect(jsonPath("$.email").value("owner@example.it"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        User updated = users.findById(original.getId()).orElseThrow();
        assertThat(updated.getLastName()).isEqualTo("Verdi");
        assertThat(updated.getPasswordHash()).isEqualTo(original.getPasswordHash());
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(original.getUpdatedAt());
        assertThat(users.findById(other.getId()).orElseThrow().getFirstName()).isEqualTo("Mario");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Anna"));
    }

    @Test void partialProfileUpdatePreservesOmittedFieldsAndCanRemovePhone() throws Exception {
        String token = token(register("owner@example.it"));
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"3331234567\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Mario"));
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Anna\",\"phone\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("3331234567"));
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Anna"));
        assertThat(users.findByEmailIgnoreCase("owner@example.it").orElseThrow().getPhone()).isNull();
    }

    @Test void invalidProfileUpdateDoesNotChangeStoredData() throws Exception {
        String token = token(register("owner@example.it"));
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "firstName", " ", "lastName", "x".repeat(101), "phone", "abc"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.lastName").exists()).andExpect(jsonPath("$.errors.phone").exists());
        assertThat(users.findByEmailIgnoreCase("owner@example.it").orElseThrow().getFirstName()).isEqualTo("Mario");
    }

    @Test void profileUpdateRequiresValidTokenAndExistingAccount() throws Exception {
        mvc.perform(patch("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Anna\"}"))
                .andExpect(status().isUnauthorized());
        String token = token(register("owner@example.it"));
        users.deleteAll();
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Anna\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String signedToken(String issuer, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("1")
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiresAt).claim("role", "USER").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
