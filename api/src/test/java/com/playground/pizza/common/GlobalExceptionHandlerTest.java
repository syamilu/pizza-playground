package com.playground.pizza.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.test.context.support.WithMockUser;
import com.playground.pizza.AbstractPostgresTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.Boom.class)
class GlobalExceptionHandlerTest extends AbstractPostgresTest {
  @RestController static class Boom {
    @GetMapping("/api/v1/boom/404") String nf() { throw new NotFoundException("order not found"); }
    @GetMapping("/api/v1/boom/400") String br() { throw new BadRequestException("bad cart"); }
    @GetMapping("/api/v1/boom/401") String ua() { throw new UnauthorizedException("bad credentials"); }
    @GetMapping("/api/v1/boom/500") String ise() { throw new IllegalStateException("kaboom"); }
  }
  @Autowired MockMvc mvc;

  @Test @WithMockUser void notFoundShape() throws Exception {
    mvc.perform(get("/api/v1/boom/404")).andExpect(status().isNotFound())
       .andExpect(jsonPath("$.error").value("order not found"))
       .andExpect(jsonPath("$.requestId").isNotEmpty());
  }
  @Test @WithMockUser void badRequestShape() throws Exception {
    mvc.perform(get("/api/v1/boom/400")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad cart"));
  }
  @Test @WithMockUser void unauthorizedShape() throws Exception {
    mvc.perform(get("/api/v1/boom/401")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("bad credentials"));
  }
  @Test @WithMockUser void unhandledIs500WithoutLeakingMessage() throws Exception {
    mvc.perform(get("/api/v1/boom/500")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("internal error"));
  }

  @Test @WithMockUser void malformedUuidIs400() throws Exception {
    mvc.perform(get("/api/v1/orders/not-a-uuid")).andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.error").value("invalid value for id"))
       .andExpect(jsonPath("$.requestId").isNotEmpty());
  }
  @Test @WithMockUser(roles = "OWNER") void unknownEnumParamIs400() throws Exception {
    mvc.perform(get("/api/v1/admin/orders").param("status", "BOGUS")).andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.error").value("invalid value for status"));
  }
  @Test @WithMockUser(roles = "OWNER") void unknownPathIs404() throws Exception {
    mvc.perform(get("/api/v1/nope")).andExpect(status().isNotFound())
       .andExpect(jsonPath("$.error").isNotEmpty()).andExpect(jsonPath("$.requestId").isNotEmpty());
  }
  @Test void unsupportedContentTypeIs415() throws Exception {
    mvc.perform(post("/api/v1/payments/callback").contentType("text/plain").content("x"))
       .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error").isNotEmpty());
  }
  @Test @WithMockUser void wrongMethodIs405() throws Exception {
    mvc.perform(delete("/api/v1/menu")).andExpect(status().isMethodNotAllowed())
       .andExpect(jsonPath("$.error").isNotEmpty());
  }
}
