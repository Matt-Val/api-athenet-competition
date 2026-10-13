<p align="center">
  <img src="docs/img/banner.png" alt="Athenet Banner" width="100%">
</p>

<p align="center">
Microservicio de competencias de <strong>Athenet</strong>: administra competencias, nómina de equipos, fixture y resultados deportivos. Consume eventos de RabbitMQ y publica los resultados que utilizan Ranking y Eventos.
</p>

<h4 align="center"><strong>
<a href="https://github.com/fabetabilo/web-athenet-org">Athenet ORG</a> - <a href="https://github.com/fabetabilo/web-athenet">Athenet Público</a>
<br>
<a href="#control-de-acceso-y-roles">Control de Acceso y Roles</a> - <a href="#desarrollo-local">Desarrollo Local</a> - <a href="#despliegue">Despliegue</a>
</strong></h4>

### Stack

<div align="center">

[![Java](https://img.shields.io/badge/Java_25-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-%23336791.svg?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-%23FF6600.svg?style=for-the-badge&logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Microsoft Azure](https://img.shields.io/badge/Microsoft_Entra_ID-%230078D4.svg?style=for-the-badge&logo=microsoftazure&logoColor=white)](https://learn.microsoft.com/entra/identity/)
[![Docker](https://img.shields.io/badge/Docker-%232496ED.svg?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-%232088FF.svg?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/features/actions)

</div>

* **Framework backend:** Spring Boot 4.1.1 (Spring Web MVC, Spring Data JPA, Spring Security y Spring AMQP).
* **Lenguaje:** Java 25.
* **Persistencia:** PostgreSQL con Hibernate/JPA; base exclusiva `competition_db` en RDS.
* **Mensajería:** RabbitMQ en EC2, exchange topic y mensajes JSON.
* **Autenticación:** OAuth2 Resource Server con JWT de Microsoft Entra ID.
* **Validación:** Bean Validation y reglas de negocio en servicios.
* **Build:** Maven mediante wrapper `./mvnw`.
* **Contenedores y despliegue:** Docker multi-stage y GitHub Actions con publicación en GHCR y despliegue manual por SSH.

### ms-competition

Competencia es la fuente de verdad de los resultados. Los partidos de su fixture se crean al consumir eventos `MATCH` asociados a una competencia desde MS-Eventos. Los datos de equipos y deportes se consultan mediante endpoints GET de MS-Instituciones; cada servicio mantiene su propia base de datos.

* **Pública (`/api/public/**`):** consulta de competencias, nómina, fixture y resultado de un evento, sin autenticación. El listado de competencias excluye `DRAFT`.
* **Administrativa (`/api/admin/competitions/**`):** gestión de competencias y equipos, resultados y simulación, reservada al rol `DIRECTOR`.

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/public/competitions?sportId&status` | Público | Listado sin borradores, con filtros opcionales |
| `GET` | `/api/public/competitions/{id}` | Público | Detalle de competencia |
| `GET` | `/api/public/competitions/{id}/teams` | Público | Nómina de equipos |
| `GET` | `/api/public/competitions/{id}/matches` | Público | Fixture y resultados |
| `GET` | `/api/public/matches?eventId={eventId}` | Público | Partido asociado al ID numérico del evento |
| `GET` | `/api/admin/competitions?sportId` | `DIRECTOR` | Listado que incluye borradores |
| `POST` | `/api/admin/competitions` | `DIRECTOR` | Crear competencia y validar el deporte |
| `PUT` | `/api/admin/competitions/{id}` | `DIRECTOR` | Actualizar competencia |
| `PATCH` | `/api/admin/competitions/{id}/status` | `DIRECTOR` | Avanzar `DRAFT` → `IN_PROGRESS` → `FINISHED` |
| `DELETE` | `/api/admin/competitions/{id}` | `DIRECTOR` | Eliminar una competencia en `DRAFT` |
| `POST` | `/api/admin/competitions/{id}/teams` | `DIRECTOR` | Inscribir equipo con `{ "teamId": 1 }` |
| `DELETE` | `/api/admin/competitions/{id}/teams/{teamId}` | `DIRECTOR` | Quitar equipo si no tiene partidos |
| `PUT` | `/api/admin/competitions/{id}/matches/{matchId}/result` | `DIRECTOR` | Registrar o corregir resultado |
| `POST` | `/api/admin/competitions/{id}/matches/{matchId}/simulate` | `DIRECTOR` | Simular un partido `SCHEDULED` |

No se permite cambiar el deporte si hay equipos inscritos. Las inscripciones comprueban que el equipo exista, esté activo y pertenezca al mismo deporte, y guardan una copia de su nombre, logo e institución.

### Control de Acceso y Roles

`SecurityConfig` valida JWT contra el issuer configurado y convierte el claim `roles` a authorities de Spring Security. Los endpoints `/api/admin/**` requieren `ROLE_DIRECTOR`.

* **`DIRECTOR`:** permite gestionar competencias, nómina, resultados y simulaciones.
* **Sin token válido:** respuesta `401` en rutas administrativas.
* **Token válido sin rol de director:** respuesta `403`; el rol `admin` no concede acceso automáticamente.
* **Rutas públicas:** `/api/public/**` no requieren token.

Enviar el access token de Microsoft Entra en `Authorization: Bearer <token>`. Configurar las audiencias admitidas según el registro de la API. CORS admite los orígenes del frontend administrativo, sin incluir rutas como `/web-athenet-org`.

Los errores de aplicación siguen el formato `ApiError`: `timestamp`, `status`, `error`, `message`. Se utilizan `400` para validación, `404` para recursos inexistentes, `409` para conflictos y `503` cuando Instituciones no responde.

### Mensajería RabbitMQ

| Routing key | Dirección | Cola | Efecto |
| :--- | :--- | :--- | :--- |
| `evento.creado` / `evento.actualizado` | Consume | `competencia.eventos` | Crea o actualiza el partido por `eventId` |
| `evento.cancelado` | Consume | `competencia.eventos` | Marca el partido `CANCELLED` |
| `resultado.registrado` | Publica | `ranking.resultados` y `eventos.resultados`, declaradas por sus consumidores | Ranking recalcula la tabla y Eventos marca el evento `FINISHED` |

El exchange es `athenet.topic`. El listener recibe el mensaje crudo y deserializa explícitamente su JSON a `EventEnvelope` con `rabbitObjectMapper`, evitando depender del header de clase Java del publicador. Si falla, hay tres reintentos después del intento inicial; al agotarlos, se rechaza sin reencolar y se dirige a `competencia.eventos.dlq` mediante `athenet.dlx`.

El resultado se publica con este contrato en inglés:

```json
{
  "messageId": "uuid",
  "occurredAt": "2026-10-12T21:00:00Z",
  "type": "resultado.registrado",
  "data": {
    "matchId": 1, "competitionId": 1, "sportId": 1, "eventId": 57,
    "version": 1,
    "homeTeamId": 1, "awayTeamId": 2,
    "homeInstitutionId": 3, "awayInstitutionId": 7,
    "homeScore": 2, "awayScore": 1, "simulated": false,
    "points": { "win": 3, "draw": 1, "loss": 0 }
  }
}
```

Ranking y Eventos deben declarar sus colas y bindings antes de publicar. La tabla y el estado del evento se actualizan de forma eventual.

### Desarrollo Local

#### 1. Verificación de dependencias del entorno

* **Java:** JDK 25.
* **PostgreSQL:** instancia local o RDS accesible, con `competition_db` y su usuario.
* **RabbitMQ:** broker accesible por AMQP; el EC2 actual utiliza puerto 5672 sin TLS.
* **MS-Instituciones:** accesible para crear competencias, inscribir equipos y simular partidos.
* **Maven:** incluido mediante wrapper.

```bash
java -version
./mvnw dependency:go-offline
```

#### 2. Configuración del entorno

Usar `.env.example` como plantilla de las variables. `DB_PASSWORD` y `RABBITMQ_PASSWORD` son obligatorias y no tienen valores predeterminados.

| Variable | Descripción |
| :--- | :--- |
| `DB_URL` | `jdbc:postgresql://<endpoint-rds>:5432/competition_db` |
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales de la BD exclusiva de Competencia |
| `JWT_ISSUER_URI` | `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES` | Audiencias admitidas para la API |
| `CORS_ALLOWED_ORIGINS` | Orígenes separados por comas, por ejemplo `http://localhost:4200,https://fabetabilo.github.io` |
| `INSTITUTION_API_URL` | URL base accesible de MS-Instituciones |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | Host y puerto del broker |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Credenciales del broker |
| `RABBITMQ_SSL` | `false` para el broker EC2 actual |
| `SERVER_PORT` | Puerto HTTP, por defecto `8082` |
| `SHOW_SQL` | Mostrar SQL, por defecto `false` |

Spring no carga `.env` automáticamente: exportar las variables en la terminal o configurar las variables del IDE antes de ejecutar Maven. También se puede crear `src/main/resources/application-local.properties`, ignorado por Git:

```properties
DB_URL=jdbc:postgresql://<endpoint-rds>:5432/competition_db
DB_USERNAME=competition_user
DB_PASSWORD=<contraseña-bd>
RABBITMQ_HOST=<host-rabbitmq>
RABBITMQ_USER=<usuario-rabbitmq>
RABBITMQ_PASSWORD=<contraseña-rabbitmq>
JWT_ISSUER_URI=https://login.microsoftonline.com/<tenant-id>/v2.0
CORS_ALLOWED_ORIGINS=http://localhost:4200,https://fabetabilo.github.io
INSTITUTION_API_URL=http://<host-instituciones>:8080
```

Nunca subir `.env`, `application-local.properties`, tokens o claves privadas al repositorio.

#### 3. Servidor de desarrollo

Con las variables exportadas:

```bash
./mvnw spring-boot:run
```

Si se usa `application-local.properties`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

La API queda disponible en `http://localhost:8082/`.

#### 4. Compilación y tests

```bash
./mvnw verify
```

Ejecuta las pruebas y genera `target/competition-0.0.1-SNAPSHOT.jar`. Las pruebas automatizadas usan dobles de repositorios, cliente REST y JWT; el test de contexto mantiene el consumidor detenido. No requieren RDS, RabbitMQ ni Entra reales y no sustituyen las pruebas de integración.

### Prueba de Resultados

Se necesita una competencia con ambos equipos inscritos, un partido creado desde `evento.creado` y un access token con rol `director`. Consultar el fixture para obtener los IDs reales.

```http
PUT /api/admin/competitions/{id}/matches/{matchId}/result
Authorization: Bearer <access-token>
Content-Type: application/json

{ "homeScore": 2, "awayScore": 1 }
```

Devuelve `200`, `status: FINISHED`, `simulated: false` y una versión incrementada. Repetir con otro marcador corrige el resultado e incrementa nuevamente `version`. Se rechazan partidos cancelados y partidos ajenos a la competencia indicada.

Para simular otro partido todavía `SCHEDULED`, sin cuerpo:

```http
POST /api/admin/competitions/{id}/matches/{matchId}/simulate
Authorization: Bearer <access-token>
```

Consulta el deporte en Instituciones: fútbol genera 0–5 y básquet 50–110 sin empates. Guarda `FINISHED`, `simulated: true` y publica el mismo tipo de mensaje. Otros deportes o partidos ya finalizados/cancelados devuelven `409`; Instituciones no disponible produce `503`.

Para observar la publicación, crear una cola temporal `prueba.resultados` y vincularla a `athenet.topic` con routing key `resultado.registrado` antes de registrar o corregir el resultado. Inspeccionar su payload desde **Get messages**. Esta cola de diagnóstico no reemplaza las colas de los servicios.

### Despliegue

El `Dockerfile` usa JDK 25 para compilar y JRE 25 para ejecutar como usuario sin privilegios.

```bash
docker build -t athenet-competition .
docker run --name athenet-competition --restart unless-stopped \
  -p 8082:8082 --env-file .env athenet-competition
```

Dentro del contenedor, `localhost` apunta al propio contenedor: usar direcciones accesibles para RDS, RabbitMQ e Instituciones.

El workflow `.github/workflows/ci-cd.yml` tiene tres etapas:

1. **Test:** ejecuta `./mvnw verify` en pull requests y pushes a `main`.
2. **Build & Push:** publica en GHCR una imagen etiquetada con el SHA del commit, después de pasar las pruebas y fuera de pull requests.
3. **Deploy manual:** desde **Actions → Competition CI/CD → Run workflow**, en `main`, despliega por SSH al EC2 usando el environment `production`.

Configurar los secrets de GitHub `EC2_APP_HOST`, `EC2_SSH_USER` y `EC2_SSH_KEY`. Preparar Docker en el EC2 y `/opt/athenet/competition.env` con las variables reales, legible por el usuario de despliegue y con permisos restringidos. Permitir acceso al puerto 8082 desde los clientes correspondientes. Para imágenes privadas, conceder al workflow acceso de lectura al paquete GHCR.

El despliegue reemplaza el contenedor existente y produce una breve interrupción. No verifica automáticamente el flujo entre servicios.

### Estructura del Proyecto

```text
src/main/java/com/athenet/competition/
├── client/       # Consultas a MS-Instituciones
├── config/       # Seguridad, CORS, RestClient y RabbitMQ
├── controller/   # Endpoints públicos y administrativos
├── dto/          # Requests y responses
├── exception/    # ApiError y manejo central de errores
├── mapper/       # Conversión entre entidades y DTO
├── messaging/    # Listener, publicador y contratos JSON
├── model/        # Competition, Enrollment, Match y enums
├── repository/   # Repositorios Spring Data JPA
└── service/      # Reglas de negocio
```

### Estado de Verificación

* **Probado manualmente:** consumo de evento y creación de partido en RDS; autenticación de director; registro/corrección del resultado; publicación inspeccionada en RabbitMQ.
* **Pruebas automatizadas:** reglas de negocio, simulación, seguridad HTTP, JSON publicado, consumo con header de clase ajena y reintentos/rechazo final.
* **Pendiente en el entorno del equipo:** simulación con Instituciones disponible, consumo real por Ranking/Eventos, llegada a DLQ en el broker y despliegue EC2.

La BD y RabbitMQ no comparten una transacción: publicar antes del commit deja una ventana de inconsistencia ante fallos. No se implementó outbox ni recuperación de publicaciones. El upsert tolera entregas repetidas, pero no compara versiones de agenda para descartar actualizaciones antiguas fuera de orden.
