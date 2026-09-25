package com.playground.pizza.auth;

import com.jayway.jsonpath.JsonPath;
import com.playground.pizza.AbstractPostgresTest;
import com.playground.pizza.common.PlaygroundProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: rolls back each test's writes (register creates real rows) so this class
// doesn't leak users into the shared singleton DB and break other tests' row-count assertions
// (e.g. SeedTest).
@Transactional
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractPostgresTest {
  @Autowired MockMvc mvc;
  @Autowired PlaygroundProperties props;

  @Test void registerThenLoginThenMe() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
        .content("{\"email\":\"new@x.local\",\"password\":\"Password123!\",\"displayName\":\"New\"}"))
       .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    String token = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
        .content("{\"email\":\"new@x.local\",\"password\":\"Password123!\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token))
       .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@x.local"));
  }

  @Test void duplicateEmailIs400() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
        .content("{\"email\":\"dupe@x.local\",\"password\":\"Password123!\",\"displayName\":\"Dupe\"}"))
       .andExpect(status().isCreated());
    mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
        .content("{\"email\":\"dupe@x.local\",\"password\":\"Password123!\",\"displayName\":\"Dupe\"}"))
       .andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.error").value("email already registered"));
  }

  @Test void registerWithoutPasswordIs400() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
        .content("{\"email\":\"nopw@x.local\",\"displayName\":\"NoPw\"}"))
       .andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.error").isNotEmpty());
  }

  @Test void wrongPasswordIs401() throws Exception {
    mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
        .content("{\"email\":\"owner@playground.local\",\"password\":\"nope\"}"))
       .andExpect(status().isUnauthorized())
       .andExpect(jsonPath("$.error").value("invalid credentials"));
  }

  @Test void meWithoutTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
       .andExpect(status().isUnauthorized())
       .andExpect(jsonPath("$.error").value("unauthorized"));
  }

  @Test void seededOwnerCanLogin() throws Exception {
    mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
        .content("{\"email\":\"owner@playground.local\",\"password\":\"Password123!\"}"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.user.role").value("OWNER"));
  }

  @Test void customerCannotAccessAdmin() throws Exception {
    String token = TestAuth.bearer(mvc, "customer01@playground.local");
    mvc.perform(get("/api/v1/admin/orders").header("Authorization", token))
       .andExpect(status().isForbidden())
       .andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test void meWithNonNumericSubjectIs401() throws Exception {
    SecretKey key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    Instant now = Instant.now();
    String token = Jwts.builder()
        .subject("not-a-number")
        .claim("email", "nobody@x.local")
        .claim("role", "CUSTOMER")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofHours(12))))
        .signWith(key)
        .compact();
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
       .andExpect(status().isUnauthorized())
       .andExpect(jsonPath("$.error").value("unauthorized"));
  }
}
