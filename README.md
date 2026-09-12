# ms-rutaexpress-catalog

Catalog microservice for the RutaExpress course project (DUOC "Desarrollo Cloud"). Owns the reference list of shipping *services* (tarifa/capacidad) offered by RutaExpress, e.g. "Envio Express", "Envio Estandar".

## Tech stack

- Java 21, Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`)
- Oracle DB via `ojdbc11`, schema managed with Flyway (`flyway-core` + `flyway-database-oracle`)
- H2 in-memory DB for tests (`spring.profiles.active=test`)
- OAuth2 resource server against Azure AD (Entra ID), same pattern as `ms-rutaexpress-bff`
- No messaging (Kafka/RabbitMQ) - catalog only exposes REST endpoints

## Running locally

Requires an Oracle DB reachable at `ORACLE_HOST:ORACLE_PORT/ORACLE_SERVICE` (defaults to `localhost:1521/XEPDB1`) and a `.env` file (gitignored) in the project root, e.g.:

```
TENANT_ID=<azure-tenant-id>
API_CLIENT_ID=<azure-api-client-id>
ORACLE_HOST=localhost
ORACLE_PORT=1521
ORACLE_SERVICE=XEPDB1
ORACLE_USER=rutaexpress
ORACLE_PASSWORD=rutaexpress
INTERNAL_API_KEY=<shared-secret-with-ms-rutaexpress-shipments>
```

Then:

```bash
./mvnw spring-boot:run
```

The service listens on port `8082` (per `infra/apps/compose.yml`). Flyway runs `V1__create_shipping_services_table.sql` automatically on startup (`spring.flyway.enabled: true`, `spring.jpa.hibernate.ddl-auto: validate`).

### Tests

```bash
./mvnw test
```

Tests run against H2 in-memory (`spring.profiles.active=test`, `spring.flyway.enabled=false`, `ddl-auto=create-drop`) and require no external database, Azure AD tenant, or network access.

### Docker

```bash
docker build -t rutaexpress/catalog:latest .
docker run --env-file .env -p 8082:8082 rutaexpress/catalog:latest
```

## Security - two tiers

1. **Team-facing endpoints** (`GET/POST /api/catalog/services`, `PUT /api/catalog/services/{id}`) require a valid Azure AD JWT (issuer `https://login.microsoftonline.com/${TENANT_ID}/v2.0`, audience `${API_CLIENT_ID}`) with authority `Admin` or `Operador`, exactly like the bff's `/api/catalog/**` rule.
2. **One internal, service-to-service endpoint** - `POST /api/catalog/services/{id}/decrease-capacity` - is called synchronously by `ms-rutaexpress-shipments` when a shipment is accepted. It does **not** require a user JWT; instead it's protected by a shared header `X-Internal-Api-Key`, validated against `internal.api-key` (env var `INTERNAL_API_KEY`, default `dev-internal-key` for local dev). This is implemented as `InternalApiKeyFilter` (a small `OncePerRequestFilter` scoped only to `POST /api/catalog/services/*/decrease-capacity` via `shouldNotFilter`), registered ahead of Spring Security's bearer-token filter. `SecurityConfig` marks that one path `permitAll()` in the OAuth2-resource-server chain since the API-key filter enforces its own auth ahead of it; every other `/api/catalog/**` path still requires the JWT + role check above.

   Missing/incorrect key -> `401 {"error":"UNAUTHORIZED","message":"..."}`.

   **Note (pending confirmation):** using a synchronous REST call for capacity decrease (instead of an async event through RabbitMQ/Kafka) is a deliberate choice - capacity is a hard business rule that must be enforced atomically before a shipment is accepted, so eventual consistency was judged unsuitable here. This is still pending final confirmation with teammate Javier; see the shared team doc at `docs/guia_javier_rutaexpress.md`.

## Endpoints

Base path: `/api/catalog/services`

### `GET /api/catalog/services?active=true`

Requires JWT (`Admin` or `Operador`). `active` query param is optional.

Response `200`:
```json
[
  {
    "id": 1,
    "name": "Envio Express",
    "description": "Entrega en 24 horas",
    "rate": 12500.00,
    "capacity": 40,
    "active": true,
    "createdAt": "2026-09-12T10:00:00Z",
    "updatedAt": "2026-09-12T10:00:00Z"
  }
]
```

### `POST /api/catalog/services`

Requires JWT (`Admin` or `Operador`). Creates with `active=true`.

Request:
```json
{
  "name": "Envio Estandar",
  "description": "Entrega en 3-5 dias habiles",
  "rate": 5000.00,
  "capacity": 100
}
```

Response `201` (with `Location: /api/catalog/services/2`):
```json
{
  "id": 2,
  "name": "Envio Estandar",
  "description": "Entrega en 3-5 dias habiles",
  "rate": 5000.00,
  "capacity": 100,
  "active": true,
  "createdAt": "2026-09-12T10:05:00Z",
  "updatedAt": "2026-09-12T10:05:00Z"
}
```

### `PUT /api/catalog/services/{id}`

Requires JWT (`Admin` or `Operador`). Partial update - only non-null fields are applied (this is the endpoint for editing tarifa/capacidad).

Request:
```json
{
  "rate": 5500.00,
  "capacity": 80
}
```

Response `200`: updated `ServiceResponse`. `404 {"error":"SERVICE_NOT_FOUND",...}` if the id doesn't exist.

### `POST /api/catalog/services/{id}/decrease-capacity`

**Internal only** - requires header `X-Internal-Api-Key: <INTERNAL_API_KEY>`, no JWT. Called by `ms-rutaexpress-shipments` when a shipment is accepted. `amount` is optional (defaults to 1).

Request:
```json
{ "amount": 1 }
```

Response `200`:
```json
{ "id": 2, "capacity": 79 }
```

Errors:
- `404 {"error":"SERVICE_NOT_FOUND","message":"..."}` - service id doesn't exist.
- `409 {"error":"CAPACITY_EXCEEDED","message":"..."}` - insufficient capacity (`capacity < amount`).
- `401 {"error":"UNAUTHORIZED","message":"..."}` - missing/wrong `X-Internal-Api-Key`.

The decrease is applied via a single atomic `UPDATE ... WHERE id = :id AND capacity >= :amount` (see `ShippingServiceRepository.decreaseCapacity`), so concurrent requests against the same service can never drive capacity negative.

## Error format

Bean validation errors -> `400`:
```json
{ "error": "VALIDATION_ERROR", "message": "...", "fields": { "name": "must not be blank" } }
```

Optimistic-locking conflicts on `PUT` (concurrent edits) -> `409`:
```json
{ "error": "CONCURRENT_UPDATE", "message": "..." }
```

## Deviations from the original spec

- **`CURRENT_TIMESTAMP` -> `CURRENT_INSTANT` in the atomic decrease-capacity query.** The spec's `@Query` used `s.updatedAt = CURRENT_TIMESTAMP`, but Hibernate 7 (bundled with Spring Boot 4.1.1) strictly type-checks HQL UPDATE assignments and rejects assigning `CURRENT_TIMESTAMP`'s `java.sql.Timestamp` result to an `Instant`-typed attribute (`SemanticException: Cannot assign expression of type 'java.sql.Timestamp' to target path 's.updatedAt' of type 'java.time.Instant'`). Used Hibernate's `CURRENT_INSTANT` HQL function instead, which returns `java.time.Instant` and keeps the update a single atomic statement as required.
- **`AntPathRequestMatcher` -> `PathPatternRequestMatcher`.** Spring Security 7 (pulled in by Boot 4.1.1) removed `org.springframework.security.web.util.matcher.AntPathRequestMatcher`; `InternalApiKeyFilter` uses `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/catalog/services/*/decrease-capacity")` instead, which matches the same single-segment-wildcard pattern.
- **Test-profile `JwtDecoder` bypass.** `SecurityConfig.jwtDecoder()` (built with `JwtDecoders.fromOidcIssuerLocation`) performs a real OIDC-discovery HTTP call to Azure AD at bean-creation time. To keep `./mvnw test` fully offline against H2 (per spec), that bean is now annotated `@Profile("!test")`, and a `test`-only replacement (`src/test/java/cl/rutaexpress/catalog/config/TestJwtDecoderConfig.java`) supplies a no-op `JwtDecoder` under the `test` profile. Production behavior (non-test profiles) is unchanged from the bff's pattern.
- Repository test uses plain JUnit `Assertions` instead of AssertJ, since AssertJ wasn't a confirmed transitive dependency of the split Boot 4 test starters used here (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`) and adding an extra dependency wasn't necessary.
- Added `spring-boot-starter-data-jpa-test` (test-scope) to `pom.xml`, needed for `@DataJpaTest`/`@AutoConfigureTestDatabase` - not part of the bff's dependency set since bff has no JPA layer.

## Build/test result

`./mvnw test` - **BUILD SUCCESS**, 7/7 tests passing (context-load smoke test, atomic decrease-capacity repository test, 5 `InternalApiKeyFilter` cases). Oracle driver and Flyway-Oracle artifacts resolved fine from Maven Central in this environment (no network restriction observed).
