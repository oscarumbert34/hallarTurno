# Turnero Backend

Base ejecutable del backend de Turnero, preparada para crecer por modulos sin implementar todavia el dominio.

## Stack

- Java 21
- Spring Boot 3.5.16
- Maven
- PostgreSQL local con Docker Compose
- Liquibase
- Spring Security
- JUnit 5, Mockito y Testcontainers

## Perfiles

- `local`: usa PostgreSQL local y define defaults de desarrollo.
- `prod`: no define credenciales por defecto; requiere variables de entorno.

## Variables

La conexion a base de datos se arma con:

| Variable | Uso | Default en `local` | Default en `prod` |
| --- | --- | --- | --- |
| `DB_HOST` | Host de PostgreSQL | `localhost` | sin default |
| `DB_PORT` | Puerto de PostgreSQL | `5432` | sin default |
| `DB_NAME` | Nombre de base | `turnero` | sin default |
| `DB_USERNAME` | Usuario | `turnero` | sin default |
| `DB_PASSWORD` | Password | `turnero` | sin default |

Tambien se pueden ajustar valores conservadores del pool Hikari:

| Variable | Default |
| --- | --- |
| `DB_POOL_MAX_SIZE` | `5` |
| `DB_POOL_MIN_IDLE` | `1` |
| `DB_POOL_CONNECTION_TIMEOUT` | `30000` |
| `DB_POOL_IDLE_TIMEOUT` | `600000` |
| `DB_POOL_MAX_LIFETIME` | `1800000` |

Variables JWT:

| Variable | Uso | Default en `local` | Default en `prod` |
| --- | --- | --- | --- |
| `JWT_SECRET` | Secreto HMAC para firmar access tokens. Debe tener al menos 32 caracteres. | valor local de desarrollo | sin default |
| `JWT_ACCESS_TOKEN_EXPIRATION` | Duracion ISO-8601 del access token. | `PT15M` | `PT15M` |

Variables de negocios:

| Variable | Uso | Default |
| --- | --- | --- |
| `BUSINESS_INITIAL_STATUS` | Estado inicial de un negocio creado por su owner. Valores: `ACTIVE`, `PENDING`, `SUSPENDED`. | `ACTIVE` |

Variables de disponibilidad:

| Variable | Uso | Default |
| --- | --- | --- |
| `AVAILABILITY_DEFAULT_ZONE_ID` | Zona horaria IANA usada por nuevas sucursales si no se informa otra. | `America/Argentina/Buenos_Aires` |
| `AVAILABILITY_SLOT_GRANULARITY_MINUTES` | Granularidad minima para generar slots disponibles. Si el servicio dura mas que esta granularidad, los inicios avanzan segun la duracion del servicio. | `15` |

Variables CORS:

| Variable | Uso | Default |
| --- | --- | --- |
| `CORS_ALLOWED_ORIGINS` | Origenes permitidos separados por coma. En produccion debe contener dominios concretos. | vacio |
| `CORS_ALLOWED_METHODS` | Metodos permitidos separados por coma. | `GET,POST,PUT,DELETE,OPTIONS` |
| `CORS_ALLOWED_HEADERS` | Headers permitidos separados por coma. | `Authorization,Content-Type` |
| `CORS_EXPOSED_HEADERS` | Headers expuestos al frontend separados por coma. | `Location` |
| `CORS_ALLOW_CREDENTIALS` | Permite credenciales CORS. Si es `true`, no se permite `*` como origen. | `false` |

Con perfil `local`, el backend permite por defecto requests desde Angular en `http://localhost:4200`.
Si el frontend corre en otro origen, configurar `CORS_ALLOWED_ORIGINS`, por ejemplo `http://localhost:4200,http://localhost:5173`.
Para el frontend productivo desplegado en Railway, usar:

```text
CORS_ALLOWED_ORIGINS=http://localhost:4200,https://hallarturnofront-production.up.railway.app
```

## Railway

Para Railway hay dos caminos soportados:

- Usar `DATABASE_URL` con formato `postgres://user:password@host:port/database`. La aplicacion lo convierte a JDBC al arrancar.
- Definir las variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`.

No hay secretos versionados. Las credenciales de `prod` deben venir siempre desde variables de entorno.

Variables requeridas para el backend en Railway:

```text
SPRING_PROFILES_ACTIVE=prod
PORT=8080
JWT_SECRET=<secreto-largo>
CORS_ALLOWED_ORIGINS=http://localhost:4200,https://hallarturnofront-production.up.railway.app
CORS_ALLOW_CREDENTIALS=false
DB_HOST=<host-postgresql>
DB_PORT=<puerto-postgresql>
DB_NAME=<database>
DB_USERNAME=<usuario>
DB_PASSWORD=<password>
```

## Ejecucion local

Levantar PostgreSQL:

```bash
docker compose up -d
```

Ejecutar la aplicacion:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Health check publico:

```bash
curl http://localhost:8080/actuator/health
```

## Docker

La imagen del backend se construye con un `Dockerfile` multi-stage: Maven compila el jar y el runtime usa JRE 21 con usuario no root. No se copian secretos ni archivos locales excluidos por `.dockerignore`.

Antes de construir la imagen, ejecutar la suite:

```bash
mvn clean test
```

Construir la imagen:

```bash
docker build -t turnero-backend .
```

Ejecutar contra PostgreSQL local expuesto en el host:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PORT=8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=turnero \
  -e DB_USERNAME=turnero \
  -e DB_PASSWORD=turnero \
  -e JWT_SECRET=change-me-with-at-least-32-characters \
  turnero-backend
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

La imagen respeta `JAVA_TOOL_OPTIONS` automaticamente y tambien permite `JAVA_OPTS`, por ejemplo:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAVA_OPTS="-XX:MaxRAMPercentage=75" \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=turnero \
  -e DB_USERNAME=turnero \
  -e DB_PASSWORD=turnero \
  -e JWT_SECRET=change-me-with-at-least-32-characters \
  turnero-backend
```

En Railway, definir `SPRING_PROFILES_ACTIVE=prod`, `JWT_SECRET` y las variables de base de datos. Railway suele inyectar `PORT`; la aplicacion lo usa mediante `server.port=${PORT:8080}`. Si se usa `DATABASE_URL`, no hace falta definir `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`.

## Seguridad

`/actuator/health`, auth, marketplace y el listado publico de negocios quedan expuestos publicamente. El resto de los endpoints requiere autenticacion JWT Bearer.

Endpoints publicos:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/businesses`
- `GET /api/v1/businesses/{businessId}/service-offerings`
- `GET /api/v1/public/availability`
- `GET /actuator/health`

La respuesta de login incluye `businessId` cuando el usuario autenticado es propietario de al menos un negocio; si no tiene negocio asociado, se devuelve `null`.

El resto de los endpoints requiere `Authorization: Bearer <token>`.

Matriz de permisos:

| Recurso | CUSTOMER | BUSINESS | ADMIN |
| --- | --- | --- | --- |
| Auth publico | Registro/login propio | Registro/login propio | No se registra desde API publica |
| Marketplace publico | Consulta anonima | Consulta anonima | Consulta anonima |
| Negocios | No administra negocios | Crea y administra solo negocios propios | Accede y administra cualquier negocio |
| Sucursales | Sin acceso privado | Administra solo sucursales de negocios propios | Accede y administra cualquier sucursal |
| Servicios reservables | Sin acceso privado | Administra solo servicios de negocios propios | Accede y administra cualquier servicio |
| Recursos reservables | Sin acceso privado | Administra solo recursos de negocios propios | Accede y administra cualquier recurso |
| Reservas | Crea reservas y cancela las propias | Cancela reservas de sus negocios | Cancela cualquier reserva |

Los chequeos de ownership se centralizan en `OwnershipGuard`. Cambiar IDs en URLs no debe permitir leer ni modificar datos de otro tenant. Los endpoints publicos nunca exponen entidades JPA ni datos sensibles de owners.

Endpoints protegidos de negocios:

- `POST /api/v1/businesses`
- `GET /api/v1/businesses/{id}`
- `PUT /api/v1/businesses/{id}`
- `DELETE /api/v1/businesses/{id}`

Endpoints protegidos de sucursales:

- `POST /api/v1/businesses/{businessId}/branches`
- `GET /api/v1/businesses/{businessId}/branches`
- `GET /api/v1/branches/{id}`
- `PUT /api/v1/branches/{id}`
- `DELETE /api/v1/branches/{id}`

Los horarios de sucursal se envian como agenda semanal por dia (`MONDAY` a `SUNDAY`) con cero o mas intervalos. Un dia cerrado se representa con `intervals: []` o sin fila de intervalos persistida. Cada intervalo debe cumplir `opensAt < closesAt` y no puede solaparse con otro intervalo del mismo dia.

Endpoints protegidos de servicios reservables:

- `POST /api/v1/businesses/{businessId}/service-offerings`
- `GET /api/v1/service-offerings/{id}`
- `PUT /api/v1/service-offerings/{id}`
- `DELETE /api/v1/service-offerings/{id}`

Los servicios usan `BigDecimal` para precio, duracion positiva entre 5 y 1440 minutos, moneda ISO-4217 de tres letras y `ARS` como default inicial. `DELETE` no borra fisicamente: marca el servicio como `INACTIVE` para no romper futuras reservas historicas.

Endpoints protegidos de recursos reservables:

- `POST /api/v1/branches/{branchId}/resources`
- `GET /api/v1/branches/{branchId}/resources`
- `GET /api/v1/resources/{id}`
- `PUT /api/v1/resources/{id}`
- `DELETE /api/v1/resources/{id}`

Los recursos reservables representan empleados inicialmente, pero el modelo soporta `EMPLOYEE`, `ROOM` y `EQUIPMENT`. Cada recurso pertenece a una sucursal, puede realizar varios servicios, y un servicio puede estar asociado a varios recursos. Los horarios laborales semanales y las ausencias se guardan en tablas consultables por dia/fecha y rango horario. No se permiten servicios de otro negocio ni servicios especificos de otra sucursal.

Disponibilidad:

`AvailabilityService` calcula slots por sucursal, servicio, fecha y recurso opcional. El algoritmo intersecta horarios de sucursal y recurso, usa la duracion real del servicio, avanza los inicios por la duracion del servicio cuando supera la granularidad minima configurada y excluye ausencias del recurso y reservas activas (`PENDING` o `CONFIRMED`).

Marketplace publico:

- `GET /api/v1/public/availability`

Parametros soportados:

| Parametro | Uso |
| --- | --- |
| `date` | Fecha obligatoria en formato `YYYY-MM-DD`. |
| `q` | Texto libre para buscar por negocio, servicio o descripcion. |
| `service` | Alias orientado a buscar por servicio; si se informa, tiene prioridad sobre `q`. |
| `startsFrom` | Hora minima del inicio del slot, formato `HH:mm`. |
| `startsTo` | Hora maxima del inicio del slot, formato `HH:mm`. |
| `locality` | Localidad exacta, sin distinguir mayusculas/minusculas. |
| `businessId` | ID del negocio para limitar la busqueda a un negocio puntual. |
| `page` | Pagina de negocios candidatos, default `0`. |
| `size` | Cantidad de negocios candidatos, default `10`, maximo `20`. |
| `maxSlotsPerService` | Slots devueltos por servicio/sucursal, default `5`, maximo `20`. |

La respuesta esta agrupada por negocio y sucursal, expone solo DTOs publicos, incluye precio/duracion de servicios y limita la cantidad de slots. Solo aparecen negocios, sucursales y servicios activos. Los slots se calculan con el mismo motor de disponibilidad, por lo que reflejan reservas confirmadas/pendientes y ausencias vigentes.

Endpoints protegidos de reservas:

- `POST /api/v1/public/bookings` para reservas publicas sin sesion
- `POST /api/v1/bookings` para reservas autenticadas
- `POST /api/v1/bookings/{id}/cancel`
- `GET /api/v1/businesses/{businessId}/bookings`

Las reservas requieren `customerName` y `customerPhone`, se crean como `CONFIRMED`, guardan snapshot de contacto, servicio, recurso, duracion, precio y moneda, y no se borran fisicamente. La cancelacion minima permite cancelar al cliente de la reserva, al owner del negocio o a `ADMIN`. El listado por negocio es paginado (`page`, `size`; maximo `50`) y solo lo puede consultar el owner del negocio o `ADMIN`. Para evitar doble booking se revalida disponibilidad dentro de la transaccion y PostgreSQL aplica una constraint de exclusion por recurso y rango horario para reservas activas.

Ejemplo de creacion de reserva:

```json
{
  "branchId": "8778f5cf-83e8-41fb-9043-83e67673650a",
  "serviceOfferingId": "7f4861a3-45b3-4817-b1ee-3ab7a2136f60",
  "resourceId": "cace2721-e7aa-43cf-a0f3-d29b711b247f",
  "date": "2026-08-26",
  "startsAt": "12:00",
  "customerName": "Juan Perez",
  "customerPhone": "+54 11 5555-1234"
}
```

Refresh tokens, rate limiting y permisos granulares quedan fuera de alcance por ahora.

## Liquibase

El changelog principal esta en:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

Los cambios se agregan como archivos incrementales en:

```text
src/main/resources/db/changelog/changes/NNN-description.yaml
```

Ejemplo:

```text
src/main/resources/db/changelog/changes/001-initial-technical-schema.yaml
```

El esquema debe cambiar solo mediante Liquibase. JPA/Hibernate usa `ddl-auto=validate`.

## Persistencia

- Naming fisico configurado en snake_case.
- `spring.jpa.open-in-view=false`.
- Timezone del backend y sesiones de base configurada en UTC.
- Hibernate no debe crear ni actualizar tablas automaticamente.

## Tests

Ejecutar:

```bash
mvn clean test
```

Pruebas incluidas:

- Context load test.
- Smoke test de `/actuator/health` publico.
- Smoke test de proteccion para endpoints no publicos.
- Test de integracion con PostgreSQL/Testcontainers para validar Liquibase y timezone UTC.
- Tests de auth para registro, login, BCrypt, JWT valido, JWT invalido y JWT expirado.
- Tests de negocios para CRUD de owner, bloqueo a otro usuario, ADMIN, slug unico y constraint de repositorio.
- Tests de sucursales para CRUD de owner, multiples sucursales, horarios cerrados, horarios invalidos/solapados y bloqueo a otro owner.
- Tests de servicios reservables para CRUD de owner, precio, duracion, moneda, sucursal ajena y desactivacion sin borrado fisico.
- Tests de recursos reservables para relaciones many-to-many, autorizacion, horarios, ausencias y referencias cruzadas entre negocios.
- Tests de disponibilidad para dia cerrado, apertura/cierre, bloqueos parciales, reservas contiguas, duraciones 30/45/60, multiples recursos y dia sin recursos.
- Tests de reservas para creacion, cancelacion por cliente/negocio, bloqueo a terceros, slot invalido y doble booking concurrente.
- Tests de marketplace publico para busqueda anonima, filtros, agrupacion, inactivos, servicios sin disponibilidad y proteccion del resto de endpoints.

