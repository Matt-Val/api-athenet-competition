<p align="center">
  <img src="docs/img/banner.png" alt="Athenet Banner" width="100%">
</p>

<p align="center">
Microservicio de competencias de <strong>Athenet</strong>: administra competencias, nómina de equipos, la llave de la copa y los resultados deportivos. Publica en RabbitMQ los resultados que utiliza Notificaciones.
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

Competencia es la dueña de los encuentros y la fuente de verdad de los resultados. Por ahora solo hay copas (`CUP`) de 16 equipos: al crear la copa se genera su llave vacía de 15 partidos (8vos, 4tos, semis y final). El director sortea los octavos y programa cada partido; el delegado asignado lo inicia y carga el resultado final. Los datos de equipos y deportes se consultan mediante endpoints GET de MS-Instituciones; cada servicio mantiene su propia base de datos.

* **Pública (`/api/public/**`):** consulta de competencias, nómina, llave, marcador de un partido y perfil de un equipo (sus competencias y últimos partidos), sin autenticación. El listado de competencias y el perfil del equipo excluyen `DRAFT`.
* **Administrativa (`/api/admin/competitions/**`):** gestión de competencias, equipos y programación de partidos, reservada al rol `DIRECTOR`.
* **Delegado (`/api/delegate/matches/**`):** iniciar, finalizar y corregir los partidos asignados, reservada al rol `DELEGATE`.

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/public/competitions?sportId&status` | Público | Listado sin borradores, con filtros opcionales |
| `GET` | `/api/public/competitions?teamId&sportId&status&limit` | Público | Competencias del equipo con su desempeño (`teamProgress`) |
| `GET` | `/api/public/competitions/{id}` | Público | Detalle de competencia |
| `GET` | `/api/public/competitions/{id}/teams` | Público | Nómina de equipos |
| `GET` | `/api/public/competitions/{id}/matches` | Público | Llave y resultados |
| `GET` | `/api/public/matches/{id}` | Público | Partido por ID |
| `GET` | `/api/public/matches?teamId&status&limit` | Público | Últimos partidos del equipo, con nombre y logo de ambos equipos |
| `GET` | `/api/admin/competitions?sportId` | `DIRECTOR` | Listado que incluye borradores |
| `GET` | `/api/admin/competitions/{id}` | `DIRECTOR` | Detalle en cualquier estado |
| `POST` | `/api/admin/competitions` | `DIRECTOR` | Crear copa, validar el deporte y generar la llave |
| `PUT` | `/api/admin/competitions/{id}` | `DIRECTOR` | Actualizar competencia |
| `PATCH` | `/api/admin/competitions/{id}/status` | `DIRECTOR` | Iniciar la copa (`DRAFT` → `IN_PROGRESS`) |
| `DELETE` | `/api/admin/competitions/{id}` | `DIRECTOR` | Eliminar una competencia en `DRAFT` y su llave |
| `GET` | `/api/admin/competitions/{id}/teams` | `DIRECTOR` | Nómina de equipos |
| `POST` | `/api/admin/competitions/{id}/teams` | `DIRECTOR` | Inscribir equipo con `{ "teamId": 1 }` (máximo 16) |
| `DELETE` | `/api/admin/competitions/{id}/teams/{teamId}` | `DIRECTOR` | Quitar equipo si no tiene partidos |
| `GET` | `/api/admin/competitions/{id}/matches` | `DIRECTOR` | Llave ordenada por ronda y posición |
| `PUT` | `/api/admin/competitions/{id}/matches/{matchId}` | `DIRECTOR` | Programar partido y sortear octavos |
| `GET` | `/api/delegate/matches` | `DELEGATE` | Partidos asignados en competencias iniciadas |
| `POST` | `/api/delegate/matches/{matchId}/start` | `DELEGATE` | Iniciar el partido |
| `PUT` | `/api/delegate/matches/{matchId}/result` | `DELEGATE` | Finalizar o corregir el resultado |

No se permite cambiar el tipo, ni el deporte si hay equipos inscritos. Las inscripciones comprueban que el equipo exista, esté activo y pertenezca al mismo deporte, y guardan una copia de su nombre, logo e institución.

#### Reglas de la copa

* **Sorteo:** los equipos solo se eligen en octavos y con la copa en `DRAFT`. Deben estar inscritos, ser distintos y no repetirse en otro partido de octavos. En las demás rondas los define el ganador del partido anterior: el partido N de una ronda alimenta al ⌈N/2⌉ de la siguiente, como local si N es impar y como visita si es par.
* **Programación:** el director edita fecha, hora, lugar y delegado mientras el partido no comience. Un partido es "completo" con ambos equipos, fecha, hora y delegado. Con la copa en curso, un partido completo no puede quedar incompleto.
* **Inicio de la copa:** `DRAFT` → `IN_PROGRESS` exige 16 equipos inscritos y los octavos sorteados. La copa pasa sola a `FINISHED` al registrar el resultado de la final.
* **Resultados:** solo el delegado asignado (claim `oid` del token igual a `delegateOid` del partido) inicia y finaliza el partido. No hay empates. Una corrección que cambia al ganador solo se acepta si el partido siguiente no ha comenzado; el director nunca registra ni corrige resultados.

#### Perfil del equipo

Ambas lecturas salen de la base de Competencia (los nombres y logos son la copia guardada al inscribir), sin llamar a Instituciones. `limit` vale 5 por defecto y acepta de 1 a 20; un `teamId` sin inscripciones devuelve `[]`.

* **Competencias del equipo:** los mismos campos del listado, las más recientes primero, más `teamProgress`: ronda más lejana (`round`), `outcome` (`ACTIVE`, `ELIMINATED` o `CHAMPION`) y partidos terminados (`played`, `won`, `lost`).
* **Últimos partidos:** `teamId` es obligatorio. Del más reciente al más antiguo, con la competencia (`id`, `name`, `season`, `competitionImg`) y cada lado como `{ teamId, name, logoUrl, score }`; `score` es `null` hasta que el partido termina y un lado es `null` si aún no tiene equipo. No incluye datos del delegado.

### Control de Acceso y Roles

`SecurityConfig` valida JWT contra el issuer configurado y convierte el claim `roles` a authorities de Spring Security. Los endpoints `/api/admin/**` requieren `ROLE_DIRECTOR` y `/api/delegate/**` requiere `ROLE_DELEGATE`.

* **`DIRECTOR`:** permite gestionar competencias, nómina y programación de partidos.
* **`DELEGATE`:** permite iniciar, finalizar y corregir solo los partidos donde es el delegado asignado; otro delegado recibe `403`.
* **Sin token válido:** respuesta `401` en rutas administrativas y de delegado.
* **Token válido sin el rol:** respuesta `403`; el rol `admin` no concede acceso automáticamente.
* **Rutas públicas:** `/api/public/**` no requieren token.

Enviar el access token de Microsoft Entra en `Authorization: Bearer <token>`. Configurar las audiencias admitidas según el registro de la API. CORS admite los orígenes del frontend administrativo, sin incluir rutas como `/web-athenet-org`.

Los errores de aplicación siguen el formato `ApiError`: `timestamp`, `status`, `error`, `message`. Se utilizan `400` para validación, `403` para un delegado que no es el asignado, `404` para recursos inexistentes, `409` para conflictos y `503` cuando Instituciones no responde.

### Mensajería RabbitMQ

Competencia solo publica; cada consumidor declara su cola, binding y DLQ. Detalle de colas, reintentos, DLQ y prueba local en [docs/dev/rabbitmq.md](docs/dev/rabbitmq.md).

| Routing key | Dirección | Cola | Efecto |
| :--- | :--- | :--- | :--- |
| `resultado.registrado` | Publica | Declarada por Notificaciones | Notificaciones avisa el resultado |

El exchange es `athenet.topic`. El resultado se publica cada vez que el delegado finaliza o corrige un partido. `version` es 1 al finalizar y sube en cada corrección: el consumidor procesa el mensaje solo si su `version` es mayor que la última guardada para ese `matchId`. Los nombres y la ronda van listos para mostrar:

```json
{
  "messageId": "uuid",
  "occurredAt": "2026-10-12T21:00:00Z",
  "type": "resultado.registrado",
  "data": {
    "matchId": 1, "competitionId": 1,
    "competitionName": "Copa Instituciones de Chile 2026",
    "sportId": 1, "round": "Cuartos de final",
    "version": 1,
    "homeTeamId": 1, "awayTeamId": 2,
    "homeTeamName": "Halcones", "awayTeamName": "Pumas",
    "homeInstitutionId": 3, "awayInstitutionId": 7,
    "homeScore": 2, "awayScore": 1,
    "points": { "win": 3, "draw": 1, "loss": 0 }
  }
}
```

Notificaciones debe declarar su cola y binding antes de que se publique.

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

#### Migración de la llave (una vez)

El esquema lo crea Hibernate (`ddl-auto=update`), que no borra columnas ni quita `NOT NULL`. Tras el cambio del modelo de partidos (llave de la copa, delegado), la tabla vieja debe borrarse una vez antes de arrancar, en local y en RDS:

```sql
DROP TABLE IF EXISTS match;
```

En el broker también se pueden borrar, desde la UI de administración, las colas `competencia.eventos` y `eventos.partidos` con sus DLQ: ya no tienen consumidor.

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

Ejecuta las pruebas y genera `target/competition-0.0.1-SNAPSHOT.jar`. Las pruebas automatizadas usan dobles de repositorios, cliente REST, RabbitTemplate y JWT. No requieren RDS, RabbitMQ ni Entra reales y no sustituyen las pruebas de integración.

### Prueba de la Copa

Con un token de rol `director`:

1. `POST /api/admin/competitions` con `type: CUP`, el deporte fútbol y `competitionImg`. Devuelve `201`; `GET .../{id}/matches` muestra los 15 partidos vacíos.
2. Inscribir 16 equipos con `POST .../{id}/teams` (el 17.º devuelve `409`).
3. Sortear y programar los octavos con `PUT .../{id}/matches/{matchId}`:

```http
PUT /api/admin/competitions/{id}/matches/{matchId}
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "date": "2026-11-08", "time": "18:00",
  "location": "Santiago, Chile", "address": "Av. Grecia 2001",
  "homeTeamId": 1, "awayTeamId": 2,
  "delegateOid": "<oid-del-delegado>", "delegateDisplayName": "Delegado de prueba"
}
```

4. `PATCH .../{id}/status` con `{ "status": "IN_PROGRESS" }`.

Con un token de rol `delegate` cuyo `oid` sea el del partido:

```http
POST /api/delegate/matches/{matchId}/start
Authorization: Bearer <access-token>
```

```http
PUT /api/delegate/matches/{matchId}/result
Authorization: Bearer <access-token>
Content-Type: application/json

{ "homeScore": 2, "awayScore": 1 }
```

Devuelve `200`, `status: FINISHED` y una versión incrementada; el ganador aparece en el partido siguiente. Repetir con otro marcador corrige el resultado e incrementa nuevamente `version`. Un empate, un partido sin iniciar o cambiar el ganador cuando el partido siguiente ya comenzó devuelven `409`; otro delegado recibe `403`.

Para observar las publicaciones, crear una cola temporal `prueba.competencia` y vincularla a `athenet.topic` con la routing key `resultado.registrado`. Inspeccionar su payload desde **Get messages**. Esta cola de diagnóstico no reemplaza las colas de los servicios.

### Despliegue

El `Dockerfile` usa JDK 25 para compilar y JRE 25 para ejecutar como usuario sin privilegios.

```bash
docker build -t athenet-competition .
docker run --name athenet-competition --restart unless-stopped \
  -p 8082:8082 --env-file .env athenet-competition
```

Dentro del contenedor, `localhost` apunta al propio contenedor: usar direcciones accesibles para RDS, RabbitMQ e Instituciones.

El workflow `.github/workflows/ci-cd.yml` tiene tres etapas:

1. **Test:** ejecuta `./mvnw verify` en pull requests y pushes a `main` o `develop`.
2. **Build & Push:** publica en GHCR una imagen etiquetada con el SHA del commit, después de pasar las pruebas y fuera de pull requests.
3. **Deploy manual:** desde **Actions → Competition CI/CD → Run workflow**, seleccionando `main` o `develop`, despliega por SSH al EC2 usando el environment `production`. Un push publica la imagen, pero no despliega. El environment debe permitir la rama seleccionada.

Configurar los secrets de GitHub `EC2_APP_HOST`, `EC2_SSH_USER` y `EC2_SSH_KEY`. Preparar Docker en el EC2 y `/opt/athenet/competition.env` con las variables reales, legible por el usuario de despliegue y con permisos restringidos. Permitir acceso al puerto 8082 desde los clientes correspondientes. Para imágenes privadas, conceder al workflow acceso de lectura al paquete GHCR.

El despliegue reemplaza el contenedor existente y produce una breve interrupción. No verifica automáticamente el flujo entre servicios.

### Estructura del Proyecto

```text
src/main/java/com/athenet/competition/
├── client/       # Consultas a MS-Instituciones
├── config/       # Seguridad, CORS, RestClient y RabbitMQ
├── controller/   # Endpoints públicos, administrativos y del delegado
├── dto/          # Requests y responses
├── exception/    # ApiError y manejo central de errores
├── mapper/       # Conversión entre entidades y DTO
├── messaging/    # Publicadores y contratos JSON
├── model/        # Competition, Enrollment, Match y enums
├── repository/   # Repositorios Spring Data JPA
└── service/      # Reglas de negocio
```

### Estado de Verificación

* **Pruebas automatizadas:** llave de 15 partidos, sorteo de octavos, avance del ganador, correcciones, empates, delegado asignado, inicio y fin de la copa, seguridad HTTP por rol, JSON publicado (`resultado.registrado`) y envío después del commit.
* **Pendiente en el entorno del equipo:** consumo real por Notificaciones y despliegue EC2.

La BD y RabbitMQ no comparten una transacción: los mensajes se envían después del commit, así que un cambio revertido nunca se publica. Si el broker falla después del commit, el cambio queda guardado, el error se registra en el log y el mensaje se pierde. No se implementó outbox ni recuperación de publicaciones.
