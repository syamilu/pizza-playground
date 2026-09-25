package com.playground.pizza.auth;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TestAuth {
  public static String bearer(MockMvc mvc, String email) throws Exception {
    String body = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(body, "$.token");
    return "Bearer " + token;
  }
}
