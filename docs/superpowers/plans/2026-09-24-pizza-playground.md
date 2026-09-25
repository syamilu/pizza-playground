# Pizza Playground Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `docker compose up` pizza ordering app (Next.js UI, Spring Boot API, Postgres, SoapUI mock payment gateway) that the Testing Knowledge Base uses as its JMeter, k6 and Playwright target.

**Architecture:** One Spring Boot 3 service with package-per-feature (`common`, `auth`, `catalog`, `orders`, `payments`, `admin`) over Postgres with Flyway. The Next.js UI is copied from the source project with every Firebase call replaced by one fetch wrapper. Payment is a SoapUI REST MockService that hosts a pay page and posts an HMAC-signed callback.

**Tech Stack:** Java 21 target (JDK 26 installed, so `maven.compiler.release=21`, no Lombok), Spring Boot 3.5.x, Spring Security + jjwt 0.12, Spring Data JPA, Flyway, Postgres 16, springdoc-openapi 2.8, Testcontainers, JUnit 5, Next.js 15, Vitest, SoapUI 5.7.2 open source, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-24-pizza-playground-design.md`

## Global Constraints

- Repo root is `/Users/syamilu/Desktop/Projects/2026/VTC/pizza-playground`. Work on branch `main`. Commit after every task.
- Brand name is **Pizza Playground**. Never mention the source client, its domain, its product names or copy anything under `public/` images from the source project.
- API base path `/api/v1`. Error body is always `{ "error": "<message>", "requestId": "<uuid>" }`.
- Order numbers start at 1001. Seed password for every seeded account is `Password123!`.
- No Lombok. No new dependencies beyond those listed in Task 2 without a reason written in the commit message.
- Every API test that touches the database uses the shared Testcontainers base class from Task 2. Docker is available locally.
- Java: `maven.compiler.release=21`. Run the API with `cd api && mvn -q test` for tests, `mvn -q spring-boot:run` locally is optional.
- Web: Node 26. Run tests with `cd web && npm test -- --run`.

## Review Focus

1. **Callback replay:** a second `POST /payments/callback` for an already PAID order must return 200 and not change `paid_at` or append a second `payments` row. Test in Task 7.
2. **Tampered amount in callback:** a callback whose `amount` differs from the order total must be rejected with 401 even if the signature over the tampered fields is valid for the mock key, because the API recomputes the signature over the stored order amount. Test in Task 7.
3. **Cart with an inactive pizza:** `POST /orders` with a `pizzaId` whose `active = false` must return 400 and create nothing. Test in Task 6.
4. **Customer reading another customer's order via `/orders/mine`:** must only return rows with the caller's `user_id`. Test in Task 6.
5. **Reset while orders exist, then place a new order:** the next order number must be 1001 again. Test in Task 8.

---

### Task 1: Repository scaffold and Docker Compose

**Files:**
- Create: `.gitignore`, `.env.example`, `docker-compose.yml`, `README.md`, `test-data/customers.csv`

**Interfaces:**
- Produces: service names `web`, `api`, `db`, `mock-gateway` and the env var names in `.env.example` that every later task reads.

- [ ] **Step 1: Write the files**

`.gitignore`:
```
node_modules/
.next/
target/
.env
*.log
.DS_Store
```

`.env.example`:
```
POSTGRES_USER=playground
POSTGRES_PASSWORD=playground
POSTGRES_DB=playground
JWT_SECRET=change-me-but-it-works-as-is-0123456789abcdef
GATEWAY_SIGNATURE_KEY=mock-signature-key
MOCK_GATEWAY_URL=http://mock-gateway:8090
PUBLIC_BASE_URL=http://localhost:3000
NEXT_PUBLIC_API_URL=http://localhost:8080
```

`docker-compose.yml`:
```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-playground}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-playground}
      POSTGRES_DB: ${POSTGRES_DB:-playground}
    ports: ["5432:5432"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-playground}"]
      interval: 5s
      timeout: 3s
      retries: 10

  api:
    build: ./api
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB:-playground}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-playground}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-playground}
      JWT_SECRET: ${JWT_SECRET:-change-me-but-it-works-as-is-0123456789abcdef}
      GATEWAY_SIGNATURE_KEY: ${GATEWAY_SIGNATURE_KEY:-mock-signature-key}
      MOCK_GATEWAY_URL: ${MOCK_GATEWAY_URL:-http://mock-gateway:8090}
      PUBLIC_BASE_URL: ${PUBLIC_BASE_URL:-http://localhost:3000}
    ports: ["8080:8080"]
    depends_on:
      db: { condition: service_healthy }
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q UP"]
      interval: 5s
      timeout: 3s
      retries: 30

  mock-gateway:
    build: ./mock-gateway
    environment:
      GATEWAY_SIGNATURE_KEY: ${GATEWAY_SIGNATURE_KEY:-mock-signature-key}
      API_CALLBACK_URL: http://api:8080/api/v1/payments/callback
    ports: ["8090:8090"]

  web:
    build:
      context: ./web
      args:
        NEXT_PUBLIC_API_URL: ${NEXT_PUBLIC_API_URL:-http://localhost:8080}
    environment:
      NEXT_PUBLIC_API_URL: ${NEXT_PUBLIC_API_URL:-http://localhost:8080}
    ports: ["3000:3000"]
    depends_on:
      api: { condition: service_healthy }
```

`test-data/customers.csv`:
```
email,password
customer01@playground.local,Password123!
customer02@playground.local,Password123!
... (through customer20, one row each)
```
Write all twenty rows explicitly.

`README.md` (short, will be expanded in Task 13):
```markdown
# Pizza Playground

A self-contained pizza ordering app used as the target for the Testing Knowledge Base (JMeter, k6, Playwright).

## Run

    cp .env.example .env
    docker compose up --build

- Shop: http://localhost:3000
- API + Swagger: http://localhost:8080/swagger-ui.html
- Mock gateway: http://localhost:8090

Design spec: `docs/superpowers/specs/2026-09-24-pizza-playground-design.md`
```

- [ ] **Step 2: Validate compose**

Run: `docker compose config > /dev/null && echo OK`
Expected: `OK` (build contexts may not exist yet; `config` only parses).

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "chore: scaffold repo, compose and env"
```

---

### Task 2: API skeleton, schema, seed, Testcontainers base

**Files:**
- Create: `api/pom.xml`, `api/Dockerfile`, `api/src/main/java/com/playground/pizza/PizzaPlaygroundApplication.java`, `api/src/main/resources/application.yml`, `api/src/main/resources/db/migration/V1__schema.sql`, `api/src/main/resources/db/migration/V2__seed.sql`, `api/src/test/java/com/playground/pizza/AbstractPostgresTest.java`, `api/src/test/java/com/playground/pizza/SeedTest.java`

**Interfaces:**
- Produces: `AbstractPostgresTest` (extend it for any DB test; gives a running Postgres and `@Autowired JdbcTemplate jdbc`), table names and columns exactly as below.

- [ ] **Step 1: pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.5</version>
    <relativePath/>
  </parent>
  <groupId>com.playground</groupId>
  <artifactId>pizza-api</artifactId>
  <version>0.1.0</version>
  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <jjwt.version>0.12.6</jjwt.version>
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.8.9</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Application class and application.yml**

```java
package com.playground.pizza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PizzaPlaygroundApplication {
  public static void main(String[] args) { SpringApplication.run(PizzaPlaygroundApplication.class, args); }
}
```

`application.yml`:
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/playground}
    username: ${SPRING_DATASOURCE_USERNAME:playground}
    password: ${SPRING_DATASOURCE_PASSWORD:playground}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
management:
  endpoints.web.exposure.include: health
playground:
  jwt-secret: ${JWT_SECRET:change-me-but-it-works-as-is-0123456789abcdef}
  gateway-signature-key: ${GATEWAY_SIGNATURE_KEY:mock-signature-key}
  mock-gateway-url: ${MOCK_GATEWAY_URL:http://localhost:8090}
  public-base-url: ${PUBLIC_BASE_URL:http://localhost:3000}
```

- [ ] **Step 3: V1__schema.sql**

```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','OWNER','CASHIER')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE pizzas (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  base_price NUMERIC(10,2) NOT NULL,
  category VARCHAR(50) NOT NULL,
  image_url VARCHAR(500) NOT NULL DEFAULT '/placeholder.svg',
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE pizza_sizes (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL, price_delta NUMERIC(10,2) NOT NULL DEFAULT 0);
CREATE TABLE crusts      (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL, price_delta NUMERIC(10,2) NOT NULL DEFAULT 0);
CREATE TABLE toppings    (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL, price_delta NUMERIC(10,2) NOT NULL DEFAULT 0);

CREATE SEQUENCE order_number_seq START WITH 1001;

CREATE TABLE orders (
  id UUID PRIMARY KEY,
  order_number BIGINT NOT NULL UNIQUE,
  user_id BIGINT REFERENCES users(id),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','NEW','PREPARING','READY','COMPLETED','CANCELLED')),
  payment_status VARCHAR(20) NOT NULL CHECK (payment_status IN ('UNPAID','PAID','FAILED')),
  type VARCHAR(20) NOT NULL CHECK (type IN ('PICKUP','DELIVERY')),
  customer_name VARCHAR(100) NOT NULL,
  customer_email VARCHAR(255) NOT NULL,
  customer_phone VARCHAR(50) NOT NULL,
  subtotal NUMERIC(10,2) NOT NULL,
  service_fee NUMERIC(10,2) NOT NULL,
  total NUMERIC(10,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at TIMESTAMPTZ
);
CREATE INDEX orders_status_idx ON orders(status);
CREATE INDEX orders_user_idx ON orders(user_id);

CREATE TABLE order_items (
  id BIGSERIAL PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  pizza_name VARCHAR(100) NOT NULL,
  size VARCHAR(50) NOT NULL,
  crust VARCHAR(50) NOT NULL,
  toppings JSONB NOT NULL DEFAULT '[]',
  quantity INT NOT NULL CHECK (quantity >= 1),
  line_price NUMERIC(10,2) NOT NULL
);

CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  gateway_bill_id VARCHAR(100),
  amount NUMERIC(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  raw_callback JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 4: V2__seed.sql**

The BCrypt hash below is for `Password123!` (cost 10). Generate it once with a throwaway Java main or `htpasswd -bnBC 10 "" 'Password123!' | tr -d ':\n'` and paste the real value; do not commit a placeholder.

```sql
INSERT INTO users (email, password_hash, display_name, role) VALUES
 ('owner@playground.local',   '<bcrypt>', 'Owner',   'OWNER'),
 ('cashier@playground.local', '<bcrypt>', 'Cashier', 'CASHIER');
INSERT INTO users (email, password_hash, display_name, role)
SELECT format('customer%02s@playground.local', n), '<bcrypt>', format('Customer %02s', n), 'CUSTOMER'
FROM generate_series(1,20) AS n;

INSERT INTO pizza_sizes (name, price_delta) VALUES ('Regular',0),('Large',6.00),('Family',12.00);
INSERT INTO crusts (name, price_delta) VALUES ('Classic',0),('Thin',0),('Stuffed',4.00);
INSERT INTO toppings (name, price_delta) VALUES
 ('Extra Cheese',2.50),('Mushroom',2.00),('Pepperoni',3.00),('Olives',1.50),
 ('Chicken',3.50),('Pineapple',1.50),('Jalapeno',1.50),('Onion',1.00);
INSERT INTO pizzas (name, description, base_price, category) VALUES
 ('Margherita','Tomato, mozzarella, basil',18.00,'classic'),
 ('Pepperoni','Pepperoni and mozzarella',22.00,'classic'),
 ('Hawaiian','Ham, pineapple, mozzarella',22.00,'classic'),
 ('Four Cheese','Mozzarella, cheddar, parmesan, blue',24.00,'classic'),
 ('BBQ Chicken','BBQ sauce, chicken, red onion',26.00,'signature'),
 ('Meat Feast','Pepperoni, beef, chicken, sausage',28.00,'signature'),
 ('Veggie Supreme','Mushroom, olives, peppers, onion',23.00,'vegetarian'),
 ('Spicy Diavola','Spicy salami, chilli, mozzarella',25.00,'signature'),
 ('Garlic Prawn','Prawns, garlic butter, rocket',29.00,'seafood'),
 ('Tuna Melt','Tuna, sweetcorn, cheddar',24.00,'seafood'),
 ('Mushroom Truffle','Mixed mushroom, truffle oil',27.00,'vegetarian'),
 ('Seasonal Special','Chef choice, ask staff',30.00,'signature');
UPDATE pizzas SET active = FALSE WHERE name = 'Seasonal Special';
```

- [ ] **Step 5: Dockerfile**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

- [ ] **Step 6: Test base and seed test**

```java
package com.playground.pizza;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractPostgresTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
  @Autowired protected JdbcTemplate jdbc;
}
```

```java
package com.playground.pizza;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SeedTest extends AbstractPostgresTest {
  @Test void seedsUsersAndMenu() {
    assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(22);
    assertThat(jdbc.queryForObject("select count(*) from pizzas where active", Integer.class)).isEqualTo(11);
    assertThat(jdbc.queryForObject("select nextval('order_number_seq')", Long.class)).isEqualTo(1001L);
  }
}
```

- [ ] **Step 7: Run**

Run: `cd api && mvn -q test`
Expected: SeedTest passes. Security auto-config will add a default login; that is fine until Task 4.

- [ ] **Step 8: Commit**

```bash
git add api && git commit -m "feat(api): skeleton, schema, seed, testcontainers base"
```

---

### Task 3: Common error handling and request ids

**Files:**
- Create: `api/src/main/java/com/playground/pizza/common/ApiError.java`, `common/NotFoundException.java`, `common/BadRequestException.java`, `common/GlobalExceptionHandler.java`, `common/PlaygroundProperties.java`
- Test: `api/src/test/java/com/playground/pizza/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `record ApiError(String error, String requestId)`; `NotFoundException(String msg)` → 404; `BadRequestException(String msg)` → 400; `@ConfigurationProperties("playground") record PlaygroundProperties(String jwtSecret, String gatewaySignatureKey, String mockGatewayUrl, String publicBaseUrl)` registered with `@ConfigurationPropertiesScan` on the application class.

- [ ] **Step 1: Failing test** (uses a throwaway controller inside the test)

```java
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.Boom.class)
class GlobalExceptionHandlerTest extends AbstractPostgresTest {
  @RestController static class Boom {
    @GetMapping("/api/v1/boom/404") String nf() { throw new NotFoundException("order not found"); }
    @GetMapping("/api/v1/boom/400") String br() { throw new BadRequestException("bad cart"); }
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
  @Test @WithMockUser void unhandledIs500WithoutLeakingMessage() throws Exception {
    mvc.perform(get("/api/v1/boom/500")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("internal error"));
  }
}
```

- [ ] **Step 2: Run, expect compile failure** (`NotFoundException` missing).

- [ ] **Step 3: Implement**

```java
public record ApiError(String error, String requestId) {}
public class NotFoundException extends RuntimeException { public NotFoundException(String m){super(m);} }
public class BadRequestException extends RuntimeException { public BadRequestException(String m){super(m);} }
```

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private ResponseEntity<ApiError> build(HttpStatus s, String msg, Exception e) {
    String id = UUID.randomUUID().toString();
    if (s.is5xxServerError()) log.error("requestId={} {}", id, e.toString(), e); else log.warn("requestId={} {}", id, msg);
    return ResponseEntity.status(s).body(new ApiError(msg, id));
  }
  @ExceptionHandler(NotFoundException.class) ResponseEntity<ApiError> nf(NotFoundException e){ return build(HttpStatus.NOT_FOUND, e.getMessage(), e); }
  @ExceptionHandler(BadRequestException.class) ResponseEntity<ApiError> br(BadRequestException e){ return build(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> val(MethodArgumentNotValidException e){
    String msg = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField()+" "+f.getDefaultMessage()).collect(Collectors.joining(", "));
    return build(HttpStatus.BAD_REQUEST, msg, e); }
  @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<ApiError> unread(HttpMessageNotReadableException e){ return build(HttpStatus.BAD_REQUEST, "malformed request body", e); }
  @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ApiError> denied(AccessDeniedException e){ return build(HttpStatus.FORBIDDEN, "forbidden", e); }
  @ExceptionHandler(Exception.class) ResponseEntity<ApiError> other(Exception e){ return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal error", e); }
}
```

`PlaygroundProperties` as the record above with `@ConfigurationProperties(prefix = "playground")`; add `@ConfigurationPropertiesScan` to `PizzaPlaygroundApplication`.

- [ ] **Step 4: Run** `cd api && mvn -q test` → all pass.
- [ ] **Step 5: Commit** `git add api && git commit -m "feat(api): global error handling with request ids"`

---

### Task 4: Auth (register, login, me, JWT, security config)

**Files:**
- Create: `auth/User.java` (JPA entity, table `users`), `auth/UserRepository.java`, `auth/Role.java` (enum CUSTOMER, OWNER, CASHIER), `auth/JwtService.java`, `auth/JwtAuthFilter.java`, `auth/SecurityConfig.java`, `auth/AuthController.java`, `auth/AuthDtos.java`
- Test: `api/src/test/java/com/playground/pizza/auth/AuthControllerTest.java`

**Interfaces:**
- Produces: `JwtService.issue(User u) : String`, `JwtService.parse(String token) : Optional<Claims>` (claims contain `sub`=user id, `email`, `role`). `JwtAuthFilter` sets `Authentication` whose principal is the `User` entity and whose authorities are `ROLE_<role>`. `SecurityConfig` permits: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/menu`, `POST /api/v1/orders`, `GET /api/v1/orders/{id}`, `POST /api/v1/payments/callback`, `GET /api/v1/payments/**`. Everything else authenticated. `/api/v1/admin/**` requires `OWNER` or `CASHIER`; method-level `@PreAuthorize("hasRole('OWNER')")` narrows further. Stateless, CSRF off, CORS allows `http://localhost:3000`. Unauthenticated → 401 with `ApiError` body `{"error":"unauthorized"}`.
- Helper for later tests: `auth/TestAuth.java` in test sources with `static String bearer(MockMvc mvc, String email) throws Exception` that POSTs login with `Password123!` and returns `"Bearer <token>"`.

- [ ] **Step 1: Failing tests**

```java
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractPostgresTest {
  @Autowired MockMvc mvc;

  @Test void registerThenLoginThenMe() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
        .content("""{"email":"new@x.local","password":"Password123!","displayName":"New"}"""))
       .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    String token = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
        .content("""{"email":"new@x.local","password":"Password123!"}"""))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token))
       .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@x.local"));
  }
  @Test void duplicateEmailIs400() throws Exception { /* register same email twice, expect 400 with error "email already registered" */ }
  @Test void wrongPasswordIs401() throws Exception { /* login owner with "nope", expect 401 and $.error == "invalid credentials" */ }
  @Test void meWithoutTokenIs401() throws Exception { /* GET /me no header → 401, $.error == "unauthorized" */ }
  @Test void seededOwnerCanLogin() throws Exception { /* login owner@playground.local / Password123! → 200, $.user.role == OWNER */ }
}
```
Write the four commented tests out in full.

- [ ] **Step 2: Run, expect failures.**

- [ ] **Step 3: Implement**

DTOs (`AuthDtos.java`, all records):
```java
public record RegisterRequest(@Email @NotBlank String email, @Size(min=8) String password, @NotBlank String displayName) {}
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
public record UserView(Long id, String email, String displayName, Role role) { static UserView of(User u){...} }
public record AuthResponse(String token, UserView user) {}
```

`JwtService` with jjwt 0.12: key = `Keys.hmacShaKeyFor(props.jwtSecret().getBytes(UTF_8))`, `issue` sets subject to user id, claims `email`, `role`, expiry now+12h. `parse` returns empty on any `JwtException`.

`JwtAuthFilter extends OncePerRequestFilter`: read `Authorization: Bearer x`, parse, load user by id, set `UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_"+role)))`.

`SecurityConfig`: `@EnableMethodSecurity`, `PasswordEncoder` = `BCryptPasswordEncoder`, `SecurityFilterChain` with the matcher list from Interfaces, `exceptionHandling().authenticationEntryPoint((req,res,e) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"error\":\"unauthorized\",\"requestId\":\""+UUID.randomUUID()+"\"}"); })`, and `accessDeniedHandler` writing 403 `forbidden` the same way.

`AuthController`: `register` → 400 `email already registered` if exists, else save with role CUSTOMER, return 201 `AuthResponse`. `login` → 401 via a dedicated `UnauthorizedException` (add to `common`, handled as 401 in `GlobalExceptionHandler` with the given message). `me` → `UserView` of principal.

- [ ] **Step 4: Run** `cd api && mvn -q test` → all pass, including Task 3 tests (they use `@WithMockUser`).
- [ ] **Step 5: Commit** `git add api && git commit -m "feat(api): jwt auth with register, login, me"`

---

### Task 5: Catalog

**Files:**
- Create: `catalog/Pizza.java`, `catalog/PizzaSize.java`, `catalog/Crust.java`, `catalog/Topping.java` (entities), `catalog/CatalogRepositories.java` (four `JpaRepository` interfaces in one file), `catalog/MenuController.java`, `catalog/MenuView.java`
- Test: `catalog/MenuControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/menu` →
```json
{ "pizzas":[{"id":1,"name":"Margherita","description":"…","basePrice":18.00,"category":"classic","imageUrl":"/placeholder.svg"}],
  "sizes":[{"id":1,"name":"Regular","priceDelta":0}], "crusts":[…], "toppings":[…] }
```
Only `active = true` pizzas. Repositories `PizzaRepository`, `PizzaSizeRepository`, `CrustRepository`, `ToppingRepository` are reused by Task 6.

- [ ] **Step 1: Failing test** — `GET /api/v1/menu` without auth → 200, `$.pizzas.length() == 11`, `$.sizes.length() == 3`, `$.toppings[0].name == "Extra Cheese"`, no pizza named `Seasonal Special`.
- [ ] **Step 2: Run, fail.**
- [ ] **Step 3: Implement** entities with `@Entity @Table(name="pizzas")` etc., `BigDecimal` for prices, plain getters. `MenuController` builds `MenuView` record from `pizzaRepository.findByActiveTrue()` and `findAll()` on the rest.
- [ ] **Step 4: Run, pass.**
- [ ] **Step 5: Commit** `git commit -am "feat(api): menu endpoint"`

---

### Task 6: Orders (pricing, create with gateway, autoPay, read)

**Files:**
- Create: `orders/Order.java`, `orders/OrderItem.java`, `orders/OrderStatus.java`, `orders/PaymentStatus.java`, `orders/OrderType.java`, `orders/OrderRepository.java`, `orders/OrderDtos.java`, `orders/PricingService.java`, `orders/OrderService.java`, `orders/OrderController.java`, `payments/GatewayClient.java`, `payments/Payment.java`, `payments/PaymentRepository.java`
- Test: `orders/PricingServiceTest.java`, `orders/OrderControllerTest.java`

**Interfaces:**
- Consumes: catalog repositories, `JwtAuthFilter` principal, `PlaygroundProperties`.
- Produces:
  - `Order` entity: `UUID id`, `long orderNumber`, `Long userId`, `OrderStatus status`, `PaymentStatus paymentStatus`, `OrderType type`, customer fields, `BigDecimal subtotal, serviceFee, total`, `Instant createdAt, paidAt`, `List<OrderItem> items` (`@OneToMany(cascade=ALL, orphanRemoval=true)`). `OrderItem.toppings` is `String` JSON column (`@JdbcTypeCode(SqlTypes.JSON)`), holding a JSON array of names.
  - `OrderRepository extends JpaRepository<Order, UUID>` with `List<Order> findByUserIdOrderByCreatedAtDesc(Long)`, `List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus)`, `List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant, Instant)`.
  - `record CreateOrderRequest(@NotNull OrderType type, @Valid @NotNull Customer customer, @NotEmpty List<@Valid Item> items, boolean autoPay)` with `record Customer(@NotBlank name, @Email @NotBlank email, @NotBlank phone)` and `record Item(@NotNull Long pizzaId, @NotNull Long sizeId, @NotNull Long crustId, List<Long> toppingIds, @Min(1) int quantity)`.
  - `record CreateOrderResponse(UUID orderId, long orderNumber, String payUrl)` (`payUrl` null when autoPay).
  - `record OrderView(UUID id, long orderNumber, String status, String paymentStatus, String type, String customerName, String customerEmail, String customerPhone, BigDecimal subtotal, BigDecimal serviceFee, BigDecimal total, Instant createdAt, Instant paidAt, List<ItemView> items)`, `record ItemView(String pizzaName, String size, String crust, List<String> toppings, int quantity, BigDecimal linePrice)`. `static OrderView of(Order)` is the one place mapping happens; Task 8 reuses it.
  - `PricingService.price(List<Item>) : PricedCart` where `record PricedCart(List<OrderItem> items, BigDecimal subtotal, BigDecimal serviceFee, BigDecimal total)`. Line price = (basePrice + size delta + crust delta + Σ topping delta) × quantity. Service fee is flat `1.00`. Throws `BadRequestException` for unknown ids or inactive pizza.
  - `GatewayClient.createBill(UUID orderId, BigDecimal amount) : BillResult` with `record BillResult(String billId, String payUrl)`; POSTs `{amount, orderId, callbackUrl, redirectUrl}` to `props.mockGatewayUrl()+"/bills"` using `RestClient`; `callbackUrl = props.publicBaseUrl()` is NOT used; the API-side callback URL is fixed to `http://api:8080/api/v1/payments/callback` via a `playground.callback-url` property (add to `PlaygroundProperties` and `application.yml`, default `http://localhost:8080/api/v1/payments/callback`); `redirectUrl = props.publicBaseUrl()+"/order-confirmation?orderId="+orderId`. Wraps any `RestClientException` in `GatewayUnavailableException` (new, in `common`, mapped to 502 `payment gateway unavailable`).
  - `OrderService.create(CreateOrderRequest, User principalOrNull) : CreateOrderResponse`: price, `nextval('order_number_seq')` via `jdbc`, save order PENDING/UNPAID; if `autoPay` → set PAID/NEW/paidAt and save a `Payment(status="PAID", amount)` in the same `@Transactional`; else call gateway **after** the transaction commits (use `TransactionTemplate` for the insert, then call gateway, then save the `Payment(gatewayBillId, amount, status="CREATED")`) so a 502 leaves the order PENDING.
  - Endpoints: `POST /api/v1/orders` (201), `GET /api/v1/orders/{id}` (404 if missing), `GET /api/v1/orders/mine` (401 without token, list of `OrderView`).

- [ ] **Step 1: PricingServiceTest (DB-backed, extends AbstractPostgresTest)**
  - Margherita Regular Classic no toppings ×1 → line 18.00, subtotal 18.00, fee 1.00, total 19.00.
  - Pepperoni Large Stuffed + Extra Cheese + Olives ×2 → (22+6+4+2.5+1.5)×2 = 72.00.
  - Unknown pizzaId 999 → BadRequestException.
  - Inactive pizza (`Seasonal Special`) → BadRequestException. *(Review Focus 3)*

- [ ] **Step 2: OrderControllerTest** using `@MockitoBean GatewayClient gateway` (Spring Boot 3.4+ annotation) and `TestAuth.bearer`:
  - `autoPay` guest order → 201, `payUrl` null, `orderNumber == 1001` on a fresh DB (or ≥1001), and `GET /orders/{id}` shows `paymentStatus PAID`, `status NEW`.
  - Non-autoPay with gateway stubbed to return `BillResult("bill_1","http://localhost:8090/pay/bill_1")` → 201 with that `payUrl`; DB has a `payments` row `status CREATED`; order is PENDING/UNPAID.
  - Gateway throws `RestClientException` → 502, order still exists PENDING/UNPAID.
  - Empty items → 400. Quantity 0 → 400.
  - `/orders/mine` for customer01 lists only their orders: create one as customer01 and one as customer02 (autoPay), each sees exactly one. *(Review Focus 4)*
  - `/orders/mine` without token → 401.
  - `GET /orders/{random uuid}` → 404.

- [ ] **Step 3: Run, fail. Step 4: Implement per Interfaces. Step 5: Run, pass.**
- [ ] **Step 6: Commit** `git add api && git commit -m "feat(api): orders with pricing, gateway bill creation and autoPay"`

---

### Task 7: Payment callback and status

**Files:**
- Create: `payments/SignatureService.java`, `payments/PaymentController.java`, `payments/PaymentService.java`
- Test: `payments/SignatureServiceTest.java`, `payments/PaymentControllerTest.java`

**Interfaces:**
- Produces:
  - `SignatureService.sign(Map<String,String> fields) : String`: HMAC-SHA256 hex over the string built from the fields `id`, `paid`, `paid_at`, `amount`, `order_id` sorted by key and joined as `key=value` with `|`, keyed with `props.gatewaySignatureKey()`. **The mock gateway (Task 9) must produce byte-identical input.** Document the exact string in a Javadoc: `amount=19.00|id=bill_1|order_id=<uuid>|paid=true|paid_at=2026-09-24T10:00:00Z`.
  - `POST /api/v1/payments/callback` accepts form-encoded or JSON body with fields `id, paid, paid_at, amount, order_id, x_signature`. Flow: load order by `order_id` (404 if missing); recompute signature using the request's `id, paid, paid_at` but the **stored order total** as `amount`; mismatch → 401 `invalid signature`. If order already PAID → 200 no change. If `paid == "true"` → PAID/NEW/paidAt=now, append `Payment(status PAID, rawCallback JSON of the fields)`. Else → paymentStatus FAILED, status stays PENDING, append `Payment(status FAILED)`. Returns `{"ok":true}`. Logs `would send email to <customer_email> for order <number>` on PAID.
  - `GET /api/v1/payments/{orderId}/status` → `{"orderId":…, "paymentStatus":"PAID", "status":"NEW", "orderNumber":1001}`.

- [ ] **Step 1: SignatureServiceTest** — known-answer test: with key `mock-signature-key` and the documented example string, assert the hex you compute once by hand with `openssl dgst -sha256 -hmac 'mock-signature-key'` and paste the value. Also assert that changing `amount` changes the signature.

- [ ] **Step 2: PaymentControllerTest** (create orders with `autoPay=false` and `@MockitoBean GatewayClient`):
  - Valid paid callback → 200, order PAID/NEW, `payments` has a PAID row, status endpoint reflects it.
  - Replay same callback → 200, `paid_at` unchanged, still exactly one PAID `payments` row. *(Review Focus 1)*
  - Tampered amount (signature computed over `amount=0.01`) → 401, order unchanged. *(Review Focus 2)*
  - Wrong key → 401.
  - `paid=false` → 200, paymentStatus FAILED, status PENDING.
  - Unknown order id → 404.

- [ ] **Step 3: Run, fail. Step 4: Implement. Step 5: Run, pass.**
- [ ] **Step 6: Commit** `git add api && git commit -m "feat(api): signed payment callback and status endpoint"`

---

### Task 8: Admin endpoints and reset

**Files:**
- Create: `admin/AdminOrderController.java`, `admin/ResetService.java`, `admin/AdminDtos.java`
- Test: `admin/AdminOrderControllerTest.java`, `admin/ResetServiceTest.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/admin/orders?status=NEW` (status optional; omitted → all non-COMPLETED, non-CANCELLED, non-PENDING) → `List<OrderView>`. OWNER or CASHIER.
  - `PATCH /api/v1/admin/orders/{id}/status` body `{"status":"PREPARING"}` → `OrderView`. Allowed transitions: NEW→PREPARING, PREPARING→READY, READY→COMPLETED, any non-COMPLETED→CANCELLED. Anything else → 400 `invalid transition <from> -> <to>`. OWNER or CASHIER.
  - `GET /api/v1/admin/orders/history?from=2026-09-01&to=2026-09-30` (ISO dates, inclusive, default last 30 days) → `List<OrderView>` of COMPLETED and CANCELLED. OWNER only (`@PreAuthorize("hasRole('OWNER')")`).
  - `POST /api/v1/admin/reset?full=false` → `{"resetAt": Instant, "full": boolean}`. OWNER only. `ResetService.reset(boolean full)` runs in one transaction: `TRUNCATE payments, order_items, orders; ALTER SEQUENCE order_number_seq RESTART WITH 1001;` and when `full`, additionally `TRUNCATE users, pizzas, pizza_sizes, crusts, toppings RESTART IDENTITY CASCADE` then re-executes `db/migration/V2__seed.sql` read from the classpath via `ScriptUtils.executeSqlScript`.

- [ ] **Step 1: Tests**
  - Cashier lists NEW orders after two autoPay orders → 2 rows. Customer token → 403. No token → 401.
  - PATCH NEW→PREPARING → 200; PREPARING→COMPLETED → 400.
  - History as cashier → 403; as owner after completing one order → contains it.
  - Reset as owner: place 2 orders, reset, `count(orders)==0`, new order gets `orderNumber 1001`. *(Review Focus 5)* Reset as cashier → 403. `full=true` leaves 22 users and 12 pizzas.

- [ ] **Step 2: Run, fail. Step 3: Implement. Step 4: Run, pass.**
- [ ] **Step 5: Commit** `git add api && git commit -m "feat(api): admin orders, history and reset"`

---

### Task 9: SoapUI mock gateway

**Files:**
- Create: `mock-gateway/Dockerfile`, `mock-gateway/gateway-mock-soapui-project.xml`, `mock-gateway/README.md`

**Interfaces:**
- Consumes: the exact signature string from Task 7 and env vars `GATEWAY_SIGNATURE_KEY`, `API_CALLBACK_URL`.
- Produces, on port 8090:
  - `POST /bills` JSON `{amount, orderId, callbackUrl, redirectUrl}` → 200 `{"id":"bill_<orderId first 8 chars>_<epoch ms>","url":"http://localhost:8090/pay/<id>"}`. Stores `amount, orderId, redirectUrl` in the mock's `context` map keyed by bill id (SoapUI `mockService.getMockRunner()` context or a static Groovy map).
  - `GET /pay/{billId}` → HTML page: order id, amount, two forms posting to `/pay/{billId}` with `action=pay` and `action=fail`. Buttons have `id="pay-button"` and `id="fail-button"` for Playwright.
  - `POST /pay/{billId}` → computes `paid_at` (ISO instant), `paid = action=="pay"`, `x_signature` = HMAC-SHA256 hex over `amount=<amount>|id=<billId>|order_id=<orderId>|paid=<true|false>|paid_at=<paid_at>` with `GATEWAY_SIGNATURE_KEY`, POSTs form-encoded to `API_CALLBACK_URL`, then responds 302 `Location: <redirectUrl>`.
  - Any request with header `X-Mock-Delay: <ms>` sleeps that long first.
  - Unknown bill id → 404 JSON `{"error":"unknown bill"}`.

- [ ] **Step 1: Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre
ARG SOAPUI_VERSION=5.7.2
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL https://dl.eviware.com/soapuios/${SOAPUI_VERSION}/SoapUI-${SOAPUI_VERSION}-linux-bin.tar.gz | tar -xz -C /opt \
 && mv /opt/SoapUI-${SOAPUI_VERSION} /opt/soapui
WORKDIR /opt/soapui
COPY gateway-mock-soapui-project.xml /project/gateway.xml
EXPOSE 8090
CMD ["/opt/soapui/bin/mockservicerunner.sh", "-m", "PaymentGatewayMock", "-p", "8090", "-b", "/project/gateway.xml"]
```
If the tarball's JRE conflicts, set `SOAPUI_HOME` and `JAVA_HOME=/opt/java/openjdk` explicitly; check `bin/mockservicerunner.sh` after extraction.

- [ ] **Step 2: Project XML**

Write a SoapUI 5.7 `con:soapui-project` with one `con:restMockService name="PaymentGatewayMock" port="8090" path="/"`. Define three `con:restMockAction` entries: `POST /bills`, `GET /pay/{billId}`, `POST /pay/{billId}`. Each action has a single default response and an `onRequest` (dispatch) Groovy script that does the work and sets the response body and status via `mockRequest.httpResponse` (`setStatus`, `setContentType`, `getWriter().write(...)`) and returns the response name. Keep bill state in a static `java.util.concurrent.ConcurrentHashMap` declared in a Groovy class inside the script (`class Bills { static final Map STORE = new ConcurrentHashMap() }`). Read env vars with `System.getenv("GATEWAY_SIGNATURE_KEY") ?: "mock-signature-key"`. HMAC via `javax.crypto.Mac`. Callback POST via `java.net.http.HttpClient` (Java 17 in the image). Honour `X-Mock-Delay` at the top of each script with `Thread.sleep`.

The easiest reliable way to get the XML skeleton right is to generate it: open SoapUI desktop, create a REST mock service with the three actions, save, and then replace the script bodies. If SoapUI desktop is not available, write the XML by hand following the schema `http://eviware.com/soapui/config`; validate by running the Docker image and issuing the curl calls in Step 3.

- [ ] **Step 3: Verify manually**

```bash
docker build -t pizza-mock mock-gateway
docker run --rm -p 8090:8090 -e API_CALLBACK_URL=http://host.docker.internal:9999/cb pizza-mock &
sleep 15
curl -s -X POST localhost:8090/bills -H 'Content-Type: application/json' \
  -d '{"amount":"19.00","orderId":"11111111-2222-3333-4444-555555555555","callbackUrl":"x","redirectUrl":"http://localhost:3000/done"}'
# expect {"id":"bill_11111111_…","url":"http://localhost:8090/pay/bill_…"}
curl -s localhost:8090/pay/<id> | grep -c 'id="pay-button"'   # expect 1
curl -s -o /dev/null -w '%{http_code}\n' localhost:8090/pay/does-not-exist  # expect 404
```
Then run `nc -l 9999` (or `python3 -m http.server 9999`) in another shell, `curl -X POST -d action=pay localhost:8090/pay/<id> -i` and confirm the callback body arrives with `x_signature` and the response is `302` to `http://localhost:3000/done`.

- [ ] **Step 4: mock-gateway/README.md** — how to open the project in SoapUI desktop, the three endpoints, the signature string, the delay header.

- [ ] **Step 5: Commit** `git add mock-gateway && git commit -m "feat(mock-gateway): soapui mock payment gateway"`

---

### Task 10: Web foundation (copy, strip Firebase, api client, auth)

**Files:**
- Create: `web/` from the source project, `web/src/lib/api.ts`, `web/src/contexts/AuthContext.tsx`, `web/src/app/login/page.tsx`, `web/src/app/register/page.tsx`, `web/Dockerfile`, `web/.env.example`
- Test: `web/tests/unit/api.test.ts`

**Interfaces:**
- Consumes: API contract from Tasks 4 to 8.
- Produces:
  - `api.ts`: `export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'`; `export class ApiError extends Error { status: number; requestId?: string }`; `export async function api<T>(path: string, init?: RequestInit & { auth?: boolean }): Promise<T>` that prefixes `/api/v1`, sets JSON headers, attaches `Authorization: Bearer <token>` from `localStorage.getItem('pp_token')` when `auth !== false` and a token exists, throws `ApiError` with the body's `error` and `requestId` on non-2xx, returns parsed JSON (or `undefined` for 204).
  - Typed helpers in the same file: `getMenu()`, `register()`, `login()`, `me()`, `createOrder()`, `getOrder(id)`, `myOrders()`, `paymentStatus(orderId)`, `adminOrders(status?)`, `updateOrderStatus(id, status)`, `orderHistory(from, to)`. Types in `src/types/api.ts` mirroring the API records.
  - `AuthContext`: `{ user, token, login(email,pw), register(...), logout(), isAdmin }`, persists token in `localStorage['pp_token']`, calls `me()` on mount to hydrate.

- [ ] **Step 1: Copy and strip**

```bash
SRC=<the source project's directory>
mkdir -p web && cd web
cp -R $SRC/src $SRC/package.json $SRC/package-lock.json $SRC/next.config.js $SRC/tsconfig.json $SRC/tailwind.config.ts $SRC/postcss.config.js $SRC/components.json $SRC/vitest.config.ts $SRC/eslint.config.js $SRC/next-env.d.ts .
mkdir -p public tests/unit && cp $SRC/public/placeholder.svg $SRC/public/robots.txt public/
cp -R $SRC/src/test src/ 2>/dev/null; cp $SRC/tests/unit/formatCurrency.test.ts $SRC/tests/unit/cartPricing.test.ts $SRC/tests/unit/cartOperations.test.tsx tests/unit/ 2>/dev/null
rm -rf src/lib/firebase.ts src/lib/firestore src/lib/storage src/emails src/app/api src/instrumentation.ts src/App.tsx src/main.tsx src/App.css src/vite-env.d.ts
rm -rf src/app/admin/{drinks,inventory,preorders,manual-order,users} src/app/examples
```
Then edit `package.json`: name `pizza-playground-web`, remove every `firebase`, `firebase-admin`, `resend`, `@react-email/*`, `@opentelemetry/*` dependency and the `migrate:rbac`, `set-claims`, `test:e2e*` scripts. Remove `remotePatterns` for firebasestorage from `next.config.js`. Run `npm install`. Grep for `firebase`, `firestore`, `billplz`, `resend` and the source brand names across `web/src`; every hit must be removed or rewritten in this task or a later one, but the app must **compile** at the end of this task, so stub any page you are not rewriting yet with a one-line component that says "coming in Task 11/12" and delete it later.

- [ ] **Step 2: Failing test `tests/unit/api.test.ts`** (Vitest, mock `global.fetch`): `api('/menu')` calls `http://localhost:8080/api/v1/menu`; attaches bearer when token in localStorage; throws `ApiError` with `status 404` and `message 'order not found'` for a 404 body `{"error":"order not found","requestId":"r1"}`.

- [ ] **Step 3: Implement `api.ts`, `types/api.ts`, `AuthContext`, login and register pages** (simple forms using existing `ui/` components; on success redirect to `/menu` for customers and `/admin/dashboard` for OWNER/CASHIER). Replace `AdminContext` usages with `AuthContext`; keep a thin `useAdmin()` export that returns `{ user, isAdmin, logout }` so existing admin pages compile.

- [ ] **Step 4: Dockerfile**

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node","server.js"]
```
Set `output: 'standalone'` in `next.config.js`.

- [ ] **Step 5: Run** `npm test -- --run` (api test + surviving unit tests pass; delete any copied unit test that only fails because its subject was Firebase) and `npm run build` succeeds.
- [ ] **Step 6: Commit** `git add web && git commit -m "feat(web): copy ui, remove firebase, add api client and auth"`

---

### Task 11: Web customer flow (menu, cart, checkout, confirmation, my orders)

**Files:**
- Modify: `web/src/app/menu/page.tsx`, `web/src/app/checkout/page.tsx`, `web/src/app/order-confirmation/page.tsx`, `web/src/contexts/CartContext.tsx`, `web/src/components/*` that read pizzas/toppings
- Create: `web/src/app/orders/page.tsx` (my orders)
- Test: existing `tests/unit/cartPricing.test.ts` adjusted to the new price model; `tests/unit/checkoutPayload.test.ts`

**Interfaces:**
- Consumes: `getMenu()`, `createOrder()`, `getOrder()`, `paymentStatus()`, `myOrders()`, `AuthContext`.
- Produces: `buildOrderPayload(cart: CartItem[], customer, type): CreateOrderRequest` exported from `src/lib/checkout.ts` (pure, testable). Cart items carry `pizzaId, sizeId, crustId, toppingIds, quantity` and display fields; client-side price is a preview computed with the same formula as the API (base + deltas) × qty plus flat fee 1.00.

- [ ] **Step 1: Failing test `checkoutPayload.test.ts`**: two cart items → payload with `items.length 2`, correct ids and quantities, `autoPay false`, `type 'PICKUP'`.
- [ ] **Step 2: Implement** `checkout.ts`, rewire `CartContext` to the new item shape, menu page loads `getMenu()` on mount (client component, loading state, error toast on `ApiError`), pizza customiser uses sizes/crusts/toppings from the menu payload, checkout page collects name/email/phone (prefilled from `user` when logged in), submits `createOrder`, then `window.location.assign(payUrl)`; confirmation page reads `orderId` from the query string, polls `paymentStatus` every 1s up to 60s, shows order number and items via `getOrder` when PAID, shows a retry link when FAILED. `/orders` page lists `myOrders()` and redirects to `/login` when no token. Add data-testid attributes: `add-to-cart`, `cart-count`, `checkout-submit`, `order-number`, `payment-status`.
- [ ] **Step 3: Run** `npm test -- --run` and `npm run build`.
- [ ] **Step 4: Commit** `git add web && git commit -m "feat(web): customer ordering flow against the api"`

---

### Task 12: Web admin flow (login, kanban, history)

**Files:**
- Modify: `web/src/app/admin/login/page.tsx`, `web/src/app/admin/dashboard/page.tsx`, `web/src/app/admin/order-history/page.tsx`, `web/src/components/admin/*` that fetched Firestore
- Delete: the Task 10 stubs.

**Interfaces:**
- Consumes: `adminOrders()`, `updateOrderStatus()`, `orderHistory()`, `AuthContext.isAdmin`.

- [ ] **Step 1: Implement.** Admin login is the same form as `/login` but redirects to `/admin/dashboard` and shows `not an admin account` for CUSTOMER tokens. Dashboard: three columns NEW, PREPARING, READY loaded with `adminOrders()` and refreshed every 5s; each card has a button for the next transition and a Cancel button, calling `updateOrderStatus`. History: date range inputs, table of COMPLETED and CANCELLED via `orderHistory`, hidden for CASHIER with a message. Remove the Firestore real-time listeners and the new-order chime. Add data-testid `kanban-column-NEW`, `order-card`, `advance-status`.
- [ ] **Step 2: Verify** `grep -rniE "firebase|firestore|billplz|resend" web/src` returns nothing; `npm run build` passes; `npm test -- --run` passes.
- [ ] **Step 3: Commit** `git add web && git commit -m "feat(web): admin kanban and history against the api"`

---

### Task 13: End-to-end compose run, smoke workflow, README

**Files:**
- Create: `.github/workflows/smoke.yml`, `scripts/smoke.sh`
- Modify: `README.md`

- [ ] **Step 1: `scripts/smoke.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail
API=${API:-http://localhost:8080}
for i in $(seq 1 60); do curl -fs "$API/actuator/health" | grep -q UP && break; sleep 2; done
TOKEN=$(curl -fs -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"customer01@playground.local","password":"Password123!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
ORDER=$(curl -fs -X POST "$API/api/v1/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"PICKUP","customer":{"name":"Smoke","email":"customer01@playground.local","phone":"0100000000"},"items":[{"pizzaId":1,"sizeId":1,"crustId":1,"toppingIds":[],"quantity":1}],"autoPay":true}')
echo "$ORDER" | grep -q '"orderNumber"' || { echo "order failed: $ORDER"; exit 1; }
OWNER=$(curl -fs -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"owner@playground.local","password":"Password123!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -fs -X POST "$API/api/v1/admin/reset" -H "Authorization: Bearer $OWNER" | grep -q resetAt
# gateway round trip
BILL=$(curl -fs -X POST "$API/api/v1/orders" -H 'Content-Type: application/json' \
  -d '{"type":"PICKUP","customer":{"name":"Smoke","email":"g@x.local","phone":"1"},"items":[{"pizzaId":1,"sizeId":1,"crustId":1,"toppingIds":[],"quantity":1}],"autoPay":false}')
ORDER_ID=$(echo "$BILL" | python3 -c 'import sys,json;print(json.load(sys.stdin)["orderId"])')
PAY_URL=$(echo "$BILL" | python3 -c 'import sys,json;print(json.load(sys.stdin)["payUrl"])')
curl -fs -o /dev/null -X POST -d action=pay "$PAY_URL"
sleep 1
curl -fs "$API/api/v1/payments/$ORDER_ID/status" | grep -q '"PAID"' || { echo "callback did not mark order paid"; exit 1; }
echo "SMOKE OK"
```

- [ ] **Step 2: `smoke.yml`**: on push and pull_request; `docker compose up -d --build`; `bash scripts/smoke.sh`; `docker compose logs` on failure; `docker compose down -v` always.

- [ ] **Step 3: Run locally** `cp .env.example .env && docker compose up -d --build && bash scripts/smoke.sh`. Fix whatever breaks (most likely: API container network name in the mock's callback URL, Next.js standalone paths). Then open `http://localhost:3000`, place an order through the UI, click Pay on the mock page, confirm the confirmation page shows an order number, log in as owner and advance it on the kanban.

- [ ] **Step 4: README** — sections: what it is, run, URLs, test accounts table, reset endpoint with curl example, `autoPay` and `X-Mock-Delay` for load testers, data-testid list for UI testers, repo layout, link to spec. No client names.

- [ ] **Step 5: Commit** `git add -A && git commit -m "feat: smoke script, ci workflow and readme"`
