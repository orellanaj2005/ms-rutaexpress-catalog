# ms-rutaexpress-catalog

Microservicio de catálogo del proyecto RutaExpress (DUOC "Desarrollo Cloud"). Administra la lista
de referencia de *servicios* de envío (tarifa/capacidad) que ofrece RutaExpress, p. ej. "Envío
Express", "Envío Estándar".

## Stack técnico

- Java 21, Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-security`,
  `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-validation`)
- Oracle DB vía `ojdbc11`, esquema administrado con Flyway (`flyway-core` +
  `flyway-database-oracle`)
- Base H2 en memoria para tests (`spring.profiles.active=test`)
- OAuth2 resource server contra Azure AD (Entra ID), mismo patrón que `ms-rutaexpress-bff`
- Sin mensajería (Kafka/RabbitMQ) — catalog solo expone endpoints REST

## Cómo correrlo localmente

Necesitas una base Oracle alcanzable en `ORACLE_HOST:ORACLE_PORT/ORACLE_SERVICE` (default
`localhost:1521/XEPDB1`, ver el repo hermano `ms-rutaexpress-db` para levantarla en Docker) y un
archivo `.env` (ignorado por git) en la raíz del proyecto, por ejemplo:

```
TENANT_ID=<tenant-id-de-azure>
API_CLIENT_ID=<api-client-id-de-azure>
ORACLE_PORT=1522
INTERNAL_API_KEY=<secreto-compartido-con-ms-rutaexpress-shipments>
```

> ⚠️ `ORACLE_PORT=1522` es necesario si usas la base de `ms-rutaexpress-db` (Docker) — ese
> contenedor expone el puerto `1522`, no el `1521` estándar, porque esta máquina ya tiene una
> instalación nativa de Oracle XE ocupándolo. Ver el README de ese repo.

Luego:

```bash
./mvnw spring-boot:run
```

El servicio escucha en el puerto `8082` (según `infra/apps/compose.yml`). Flyway corre
`V1__create_shipping_services_table.sql` automáticamente al arrancar (`spring.flyway.enabled:
true`, `spring.jpa.hibernate.ddl-auto: validate`).

### Tests

```bash
./mvnw test
```

Los tests corren contra H2 en memoria (`spring.profiles.active=test`, `spring.flyway.enabled=false`,
`ddl-auto=create-drop`) y no requieren base de datos externa, tenant de Azure AD, ni acceso de red.

### Docker

```bash
docker build -t rutaexpress/catalog:latest .
docker run --env-file .env -p 8082:8082 rutaexpress/catalog:latest
```

## Seguridad — dos niveles

1. **Endpoints normales** (`GET/POST /api/catalog/services`, `PUT /api/catalog/services/{id}`)
   requieren un JWT válido de Azure AD (issuer `https://login.microsoftonline.com/${TENANT_ID}/v2.0`,
   audience `${API_CLIENT_ID}`) con autoridad `Admin` u `Operador`, igual que la regla del bff
   para `/api/catalog/**`.
2. **Un endpoint interno, service-to-service** — `POST /api/catalog/services/{id}/decrease-capacity`
   — es llamado sincrónicamente por `ms-rutaexpress-shipments` cuando se acepta un envío. **No**
   requiere JWT de usuario; en su lugar está protegido por un header compartido
   `X-Internal-Api-Key`, validado contra `internal.api-key` (variable `INTERNAL_API_KEY`, default
   `dev-internal-key` para desarrollo local). Está implementado como `InternalApiKeyFilter` (un
   `OncePerRequestFilter` acotado solo a `POST /api/catalog/services/*/decrease-capacity` vía
   `shouldNotFilter`), registrado antes del filtro de bearer-token de Spring Security.
   `SecurityConfig` marca ese path como `permitAll()` en la cadena OAuth2, ya que el filtro de
   API key aplica su propia autenticación antes; cualquier otro path bajo `/api/catalog/**`
   sigue requiriendo el JWT + rol de arriba.

   Key faltante o incorrecta → `401 {"error":"UNAUTHORIZED","message":"..."}`.

   **Nota (pendiente de confirmación):** usar una llamada REST síncrona para decrementar
   capacidad (en vez de un evento asíncrono por RabbitMQ/Kafka) es una decisión deliberada — la
   capacidad es una regla de negocio dura que debe aplicarse atómicamente antes de aceptar un
   envío, así que la consistencia eventual se consideró inadecuada aquí. Sigue pendiente de
   confirmación final con el compañero Javier; ver el documento compartido del equipo en
   `docs/guia_javier_rutaexpress.md`.

## Endpoints

Base path: `/api/catalog/services`

### `GET /api/catalog/services?active=true`

Requiere JWT (`Admin` u `Operador`). El parámetro `active` es opcional.

Respuesta `200`:
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

Requiere JWT (`Admin` u `Operador`). Crea con `active=true`.

Request:
```json
{
  "name": "Envio Estandar",
  "description": "Entrega en 3-5 dias habiles",
  "rate": 5000.00,
  "capacity": 100
}
```

Respuesta `201` (con `Location: /api/catalog/services/2`):
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

Requiere JWT (`Admin` u `Operador`). Actualización parcial — solo se aplican los campos no
nulos (este es el endpoint para editar tarifa/capacidad).

Request:
```json
{
  "rate": 5500.00,
  "capacity": 80
}
```

Respuesta `200`: `ServiceResponse` actualizado. `404 {"error":"SERVICE_NOT_FOUND",...}` si el id
no existe.

### `POST /api/catalog/services/{id}/decrease-capacity`

**Solo interno** — requiere header `X-Internal-Api-Key: <INTERNAL_API_KEY>`, sin JWT. Lo llama
`ms-rutaexpress-shipments` al aceptar un envío. `amount` es opcional (default 1).

Request:
```json
{ "amount": 1 }
```

Respuesta `200`:
```json
{ "id": 2, "capacity": 79 }
```

Errores:
- `404 {"error":"SERVICE_NOT_FOUND","message":"..."}` — el id de servicio no existe.
- `409 {"error":"CAPACITY_EXCEEDED","message":"..."}` — capacidad insuficiente (`capacity < amount`).
- `401 {"error":"UNAUTHORIZED","message":"..."}` — `X-Internal-Api-Key` faltante o incorrecta.

El decremento se aplica con un único `UPDATE ... WHERE id = :id AND capacity >= :amount` atómico
(ver `ShippingServiceRepository.decreaseCapacity`), así que solicitudes concurrentes contra el
mismo servicio nunca pueden dejar la capacidad en negativo.

## Formato de errores

Errores de validación (Bean Validation) → `400`:
```json
{ "error": "VALIDATION_ERROR", "message": "...", "fields": { "name": "must not be blank" } }
```

Conflictos de bloqueo optimista en `PUT` (ediciones concurrentes) → `409`:
```json
{ "error": "CONCURRENT_UPDATE", "message": "..." }
```

## Desviaciones respecto a la especificación original

- **`CURRENT_TIMESTAMP` → `CURRENT_INSTANT` en el query atómico de decrease-capacity.** La
  especificación original usaba `s.updatedAt = CURRENT_TIMESTAMP`, pero Hibernate 7 (incluido en
  Spring Boot 4.1.1) valida estrictamente los tipos en asignaciones HQL de UPDATE y rechaza
  asignar el `java.sql.Timestamp` de `CURRENT_TIMESTAMP` a un atributo de tipo `Instant`
  (`SemanticException: Cannot assign expression of type 'java.sql.Timestamp' to target path
  's.updatedAt' of type 'java.time.Instant'`). Se usó la función HQL `CURRENT_INSTANT` de
  Hibernate en su lugar, que retorna `java.time.Instant` y mantiene el update como una sola
  sentencia atómica, tal como se requiere.
- **`AntPathRequestMatcher` → `PathPatternRequestMatcher`.** Spring Security 7 (traído por Boot
  4.1.1) eliminó `org.springframework.security.web.util.matcher.AntPathRequestMatcher`;
  `InternalApiKeyFilter` usa en su lugar `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,
  "/api/catalog/services/*/decrease-capacity")`, que matchea el mismo patrón con wildcard de un
  segmento.
- **Bypass del `JwtDecoder` en el perfil de test.** `SecurityConfig.jwtDecoder()` (construido con
  `JwtDecoders.fromOidcIssuerLocation`) hace una llamada HTTP real de descubrimiento OIDC contra
  Azure AD al crear el bean. Para mantener `./mvnw test` completamente offline contra H2 (según lo
  pedido), ese bean ahora está anotado `@Profile("!test")`, y un reemplazo exclusivo del perfil
  `test` (`src/test/java/cl/rutaexpress/catalog/config/TestJwtDecoderConfig.java`) provee un
  `JwtDecoder` no-op bajo el perfil `test`. El comportamiento en producción (perfiles distintos de
  `test`) no cambia respecto al patrón del bff.
- El test de repositorio usa `Assertions` de JUnit plano en vez de AssertJ, ya que AssertJ no
  era una dependencia transitiva confirmada de los starters de test de Boot 4 usados aquí
  (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`) y no era necesario
  agregar una dependencia extra.
- Se agregó `spring-boot-starter-data-jpa-test` (scope test) al `pom.xml`, necesario para
  `@DataJpaTest`/`@AutoConfigureTestDatabase` — no forma parte del set de dependencias del bff
  porque el bff no tiene capa JPA.

## Registro de cambios

### 2026-09-12 — Implementación inicial (Jassack)
Se construyó el microservicio completo desde cero según lo pedido: CRUD de servicios de catálogo
(tarifa/capacidad) y el endpoint interno atómico de decremento de capacidad, protegido con API
key en vez de JWT para las llamadas service-to-service desde shipments.

### 2026-09-12 — Esquema Oracle propio, separado de shipments (Jassack)
Al levantar la base local en Docker (`ms-rutaexpress-db`) y correr las migraciones de Flyway de
ambos servicios contra la misma base, `catalog` y `shipments` quedaron configurados por defecto
con el **mismo usuario/esquema Oracle** (`rutaexpress`). Esto rompe Flyway: los dos servicios
numeran su primera migración como `V1` pero con contenido distinto
(`V1__create_shipping_services_table.sql` vs `V1__create_shipments_table.sql`), y al compartir
esquema Flyway detecta un "checksum mismatch" al migrar el segundo servicio. Cada microservicio
debe tener su propio esquema Oracle — es la forma correcta de aislar datos entre servicios en una
arquitectura de microservicios, y además evita este choque de versiones de Flyway. Se cambió el
usuario Oracle por defecto de catalog de `rutaexpress` a **`catalog`** (`ORACLE_USER`/`ORACLE_PASSWORD`
en `application.yaml`), y se documentó la creación de ese usuario en `ms-rutaexpress-db/initdb/`.

### 2026-09-12 - Dos bugs encontrados y corregidos probando contra Oracle real (Jassack)
Al levantar el servicio contra la base de Docker y probar los endpoints con un JWT real de
Postman aparecieron dos problemas que los tests con H2 no detectaban (H2 es más permisivo que
Oracle real con estos dos tipos):

1. **Mapeo de `active` (boolean) incorrecto**: el arranque fallaba con `SchemaManagementException:
   wrong column type encountered in column [active]... found [number], but expecting [boolean]`.
   Oracle 23ai tiene un tipo `BOOLEAN` nativo, y Hibernate 7 mapea `boolean` de Java a ese tipo por
   defecto en ese dialecto, pero la migración creó la columna como `NUMBER(1)` (compatible con
   versiones más viejas de Oracle). Se agregó `@Convert(converter = NumericBooleanConverter.class)`
   al campo `active` para que Hibernate lo siga tratando como 0/1 en vez de esperar el tipo nativo.
2. **ORA-18716 al leer servicios**: `GET /api/catalog/services` devolvía 500 con `ORA-18716: {0}
   no está en ninguna zona horaria`. Es un problema de compatibilidad entre `ojdbc11` y el tipo
   JDBC que Hibernate 7 usa por defecto para campos `Instant` (llama a
   `ResultSet.getObject(col, OffsetDateTime.class)`, que el driver de Oracle no soporta bien contra
   una columna `TIMESTAMP` plana). No se resolvió cambiando la zona horaria de la JVM ni con la
   propiedad `oracle.jdbc.timezoneAsRegion` - el fix fue forzar a Hibernate a usar el tipo JDBC
   `TIMESTAMP` clásico (`getTimestamp()`) para `createdAt`/`updatedAt` vía
   `@JdbcTypeCode(SqlTypes.TIMESTAMP)`, sin cambiar el tipo Java `Instant` que ya usa el resto del
   código. Mismo bug y mismo fix en `ms-rutaexpress-shipments`.

## Resultado del build/tests

`./mvnw test` — **BUILD SUCCESS**, 7/7 tests pasando (smoke test de carga de contexto, test
atómico del repositorio de decrease-capacity, 5 casos de `InternalApiKeyFilter`). El driver
Oracle y los artefactos de Flyway-Oracle se resolvieron sin problema desde Maven Central en este
entorno (no se observaron restricciones de red).
