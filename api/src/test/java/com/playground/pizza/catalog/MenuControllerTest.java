package com.playground.pizza.catalog;

import com.playground.pizza.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MenuControllerTest extends AbstractPostgresTest {
  @Autowired MockMvc mvc;

  @Test void menuIsPublicAndListsOnlyActivePizzas() throws Exception {
    mvc.perform(get("/api/v1/menu"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.pizzas.length()").value(11))
       .andExpect(jsonPath("$.sizes.length()").value(3))
       .andExpect(jsonPath("$.crusts.length()").value(3))
       .andExpect(jsonPath("$.toppings.length()").value(8))
       .andExpect(jsonPath("$.toppings[0].name").value("Extra Cheese"))
       .andExpect(jsonPath("$.pizzas[?(@.name=='Seasonal Special')]").isEmpty());
  }

  @Test void swaggerUiHtmlIsPublic() throws Exception {
    mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }
}
