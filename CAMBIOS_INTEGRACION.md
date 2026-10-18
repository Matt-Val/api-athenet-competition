# Cambios de integración — Competencia

Fecha: 18 de octubre de 2026. Documento de entrega para el responsable de este repositorio.

## Objetivo

Conectar la agenda de encuentros con Eventos y publicar resultados y correcciones para Notificaciones y Ranking. Competencia conserva la propiedad del encuentro y la autorización del delegado asignado.

## Qué cambió y por qué

### Publicación mediante outbox

Se sustituyó `AfterCommitSender` por una outbox persistida. Antes, un envío posterior al commit podía fallar después de guardar el resultado sin dejar una tarea durable para recuperarlo.

Se añadieron `OutboxMessage`, `OutboxMessageRepository`, `OutboxEventWriter`, `OutboxPublisher` y `OutboxScheduler`. El escritor exige una transacción existente y guarda el JSON del mensaje junto con la operación de dominio. El publicador procesa pendientes en una transacción separada, envía JSON persistente con publisher confirms y mandatory/returns, y registra publicación o próximo intento. Conserva el ID, la fecha original, la revisión y el payload al reintentar.

La consulta usa `FOR UPDATE SKIP LOCKED` y evita adelantar una revisión si hay otra anterior pendiente del mismo tipo/agregado. El scheduler está habilitado por defecto, consulta cada 2 segundos y procesa hasta 50 pendientes por ciclo. Los reintentos tienen espera creciente. Un ACK del broker acredita publicación; el consumo se comprueba en cada servicio.

`ResultEventPublisher` ahora escribe `resultado.registrado` en esta outbox. El mensaje incorpora `matchDate` opcional en formato ISO para que Ranking pueda usar la fecha del encuentro.

### Agenda explícita y copia en Eventos

`Match`, DTOs y mapper incorporan `endDate`, `endTime`, `scheduleConfirmed` y `scheduleVersion`. Se añadió al controlador administrativo:

```http
POST /api/admin/competitions/{competitionId}/matches/{matchId}/confirm
```

La confirmación valida equipos, delegado, inicio/término válidos y portada de la copa. `ScheduleEventPublisher` genera `partido.actualizado`, que Eventos consume para crear o actualizar la copia. Confirmar de nuevo una agenda ya confirmada no crea otra revisión.

La confirmación es independiente del inicio de la copa. Se conserva la condición existente de inicio: 16 inscritos y todos los octavos con equipos asignados. Para iniciar un encuentro, el delegado necesita además que su agenda esté confirmada.

Si cambia una agenda confirmada, se publica la cancelación del snapshot anterior y se exige reconfirmación. El historial de outbox permite recuperar ese snapshot. La eliminación de una copa DRAFT cancela sus copias confirmadas; cambios de metadatos de la copa refrescan las agendas confirmadas. Un cambio de ganador puede invalidar la programación del encuentro siguiente si cambia un participante.

### Resultados y escrituras concurrentes

Se añadió un bloqueo pesimista de la competencia en operaciones que modifican copa, inscripciones, agenda o resultados. Se busca serializar cambios que comparten llave y participantes. Para resultados se obtiene primero el ID de competencia antes de cargar el encuentro. Los conflictos de bloqueo se traducen a HTTP 409.

Guardar nuevamente el mismo marcador de un encuentro terminado devuelve el estado existente sin incrementar la versión ni crear otro mensaje. Una corrección distinta incrementa la versión y conserva el flujo existente de avance del ganador. Se mantienen las reglas de rol y OID del delegado; no se añadieron excepciones de autenticación para las pruebas.

Se retiró `@AllArgsConstructor` de `Competition` y `Match`: sus constructores de todos los campos no se utilizan en el código ni en las pruebas y el editor marcaba errores sobre ellos. Se conservan `@NoArgsConstructor`, getters/setters y los valores iniciales de estado y puntos.

## Archivos y pruebas

- `messaging/Outbox*`, `model/OutboxMessage.java`, `repository/OutboxMessageRepository.java`: persistencia y publicación recuperable.
- `messaging/ScheduleEventPublisher.java`: contrato de agenda y snapshots de cancelación.
- `MatchService`, `CompetitionService`, `EnrollmentService` y repositorios: confirmación, idempotencia y bloqueos.
- Controlador, DTOs, mapper y manejador de errores: contrato HTTP actualizado.
- Aplicación/configuración: scheduling, publisher confirms, returns y vhost configurable.
- Pruebas de publicador, outbox y servicios actualizadas; retiradas las pruebas del sender eliminado. README actualizado.

La suite completa pasó con 61 pruebas después de `clean test` en el entorno de verificación. Las pruebas con dobles del broker no demuestran recuperación real ante una caída del broker.

## Esquema y configuración

Por decisión del usuario, los scripts de `db/migration` quedan fuera de los commits de esta entrega. No se ejecutó ninguna migración en RDS ni se añadió Flyway.

La aplicación conserva `spring.jpa.hibernate.ddl-auto=update`. El código necesita la tabla `domain_outbox` y los campos nuevos de `match`; Hibernate generó las estructuras en el esquema vacío de integración. Excluir los archivos SQL no elimina esa dependencia del esquema. La comprobación y actualización de un esquema RDS existente queda para el despliegue.

El director debe completar el término y confirmar los encuentros antes de iniciarlos. No se deben borrar outboxes publicadas sin resolver antes la conservación del historial de agenda.

Configurar PostgreSQL, URL de Instituciones, JWT de Entra y RabbitMQ, incluido `RABBITMQ_VHOST`. El exchange compartido es `athenet.topic`; las rutas publicadas son `partido.actualizado` y `resultado.registrado`. Los consumidores deben tener sus colas/bindings preparados. `app.messaging.outbox-enabled` permite controlar el scheduler y `app.messaging.outbox-delay` su intervalo.

## Validación local conectada y límites

Con JWT reales y BFF, el usuario confirmó el encuentro 1: se publicó la revisión de agenda 1 y Eventos creó `PARTIDO-1`. Después inició copa y encuentro, registró 2–1 y corrigió a 1–3. Se verificaron ambas revisiones de resultado publicadas, entregas SENT y sustitución del ranking manteniendo un partido por equipo.

La aplicación de migraciones sobre RDS, caída del broker durante una escritura, pruebas entre varias instancias, casos negativos exhaustivos de permisos y reprogramación/cancelación conectadas quedaron fuera del cierre mínimo. No se desplegó en AWS.
