# BACKEND_REQUIREMENTS — Residencia Guadalupe News (app Android)

Reporte de lo que el frontend Android espera de la API. Generado a partir de la revisión completa del código en `app/` el 2026-09-22.

**Convenciones de este documento**

- `…/news/` abrevia `app/src/main/java/com/residenciaguadalupe/news/`.
- **[Encontrado]**: el requisito aparece en el código (pantalla, mock, DTO o configuración).
- **[DTO]**: aparece solo en los DTOs de `…/news/data/red/Dtos.kt` y `Envolturas.kt`. Esos DTOs describen un contrato previsto, pero **ninguna pantalla los usa todavía**.
- **[Propuesta]**: recomendación de este reporte. No existe en el código y debe confirmarse.
- La URL base se configura fuera del repositorio (`rg.api.url` en `local.properties`) y no se documenta aquí. Todas las rutas de este documento son relativas a `/api/v1`.
- Los DTOs **no definen rutas HTTP**: no existe una interfaz Retrofit. Todas las rutas son **[Propuesta]**, aunque sus cuerpos vengan de un DTO.

---

# 1. Resumen ejecutivo

## Módulos y flujos del frontend

La app es una sola `Activity` en Jetpack Compose con 9 pantallas (`…/news/Navegacion.kt:20-30`):

| # | Módulo | Pantallas |
|---|---|---|
| 1 | Acceso | Login, registro y confirmación de cuenta creada (`AccesoScreen.kt`) |
| 2 | Muro de publicaciones | Muro con filtros, banda «Hoy en la residencia», tarjetas (`MuroScreen.kt`) |
| 3 | Agenda | Actividades de Hoy / Mañana / Esta semana (`HoyScreen.kt`) |
| 4 | Detalle de publicación | Detalle con autor, foto, ficha Cuándo/Dónde/Publica y lectura en voz alta (`DetalleScreen.kt`) |
| 5 | Notificaciones | Buzón con leídas/sin leer, «Marcar todas», «Archivar las leídas» e insignia en la barra (`AvisosScreen.kt`, `BarraInferior.kt`) |
| 6 | Perfil | Datos del residente, tamaño de letra, preferencias de avisos, «Agendar visita familiar» y cerrar sesión (`PerfilScreen.kt`) |
| 7 | Archivo | Publicaciones anteriores agrupadas por mes (`ArchivoScreen.kt`) |
| 8 | Administración: lista | Avisos con Editar / Fijar / Archivar / Eliminar y Deshacer (`AdminListaScreen.kt`) |
| 9 | Administración: crear/editar | Formulario de aviso y confirmación con vista previa (`AdminCrearScreen.kt`) |

## Integración HTTP actual

**No existe ninguna integración HTTP funcional.**

- Hay dependencias de Retrofit 2.11, OkHttp 4.12, kotlinx-serialization y Coil (`app/build.gradle.kts:74-87`).
- Hay DTOs y envolturas de respuesta (`…/news/data/red/Dtos.kt`, `…/news/data/red/Envolturas.kt`), pero ningún archivo fuera de `data/red/` los referencia.
- No hay interfaz Retrofit, repositorio ni almacenamiento de sesión. Las carpetas `…/news/data/repositorio/` y `…/news/data/sesion/` existen **vacías**.
- `AplicacionResidencia.kt:8-10` indica que el cliente OkHttp autenticado, Retrofit y Coil «se crearán» ahí. Hoy la clase está vacía.
- Las reglas de respaldo excluyen `sesion_residencia.xml` (`res/xml/reglas_respaldo.xml`, `reglas_extraccion.xml`). Eso anticipa guardar la sesión en SharedPreferences, pero el archivo todavía no se escribe.

## Mocks, datos estáticos y comportamiento local

- Todo el contenido viene de `…/news/data/DatosEjemplo.kt`: publicaciones, agenda, notificaciones, avisos de administración y archivo.
- Las pantallas Detalle y Perfil, y el encabezado del Muro, tienen **textos escritos directamente en el componente**: nombre «Sebastián», «Habitación 112», el aviso de jardinería completo y otros.
- Login y registro **no validan nada**. Cualquier botón entra al muro (`AccesoScreen.kt:116-118`).
- Las acciones de administración, notificaciones y preferencias solo modifican listas en memoria (`AppViewModel.kt`).
- El estado de carga del muro es un `delay(750)` simulado (`AppViewModel.kt:47-56`).

## Necesidades principales del backend

1. Autenticación con token para Android: registro, login, sesión actual y cierre de sesión.
2. Listado y detalle de publicaciones con filtros por categoría, estado de lectura, fijado y adjuntos incluidos.
3. Agenda de actividades o eventos por rango de fechas: hoy, mañana y el resto de la semana.
4. Buzón de notificaciones por usuario con conteo de no leídas. **No existe ningún DTO para esto.**
5. Perfil del residente con habitación, fecha de ingreso y contacto familiar. **El contacto familiar no existe en ningún DTO.**
6. Administración de publicaciones: crear, editar, fijar/desfijar, archivar/republicar y eliminar.
7. Carga y descarga autenticada de fotografías y PDF.

## Bloqueadores para conectar con una API real

| # | Bloqueador | Evidencia |
|---|---|---|
| B1 | **Las categorías de la UI no coinciden con el contrato.** La UI usa `AVISO/ACTIVIDAD/EVENTO/NOTICIA`. El DTO usa `type` = `menu/notice/event` y `category` = `Actividad/Salud/Capilla/Comida`. Sin una tabla de equivalencias no se pueden filtrar ni crear publicaciones. | `data/Modelos.kt:6-11`, `data/red/Dtos.kt:85-86` |
| B2 | **El contrato no tiene «fijado».** La UI maneja `FIJADO/PUBLICADO/ARCHIVADO`, pero el DTO solo tiene `status` = `draft/published/archived`. | `data/Modelos.kt:14-18`, `Dtos.kt:89` |
| B3 | **El formulario de administración usa texto libre.** «Día» y «Hora» (`"Viernes 18"`, `"4:00 p. m."`) no pueden convertirse a `eventStartAt`, `menuDate` ni `expiresAt`. Además no pide tipo, lugar, hora de fin ni tipo de comida. | `AdminCrearScreen.kt:73-76`, `AppViewModel.kt:187` |
| B4 | **Notificaciones sin contrato.** No hay DTO ni endpoint. | `data/Modelos.kt:41-48` |
| B5 | **Detalle sin identificador.** La ruta `detalle` no recibe `postId` y la pantalla está fija con datos de ejemplo. | `Navegacion.kt:82-84`, `DetalleScreen.kt:54-96` |
| B6 | **No existe capa de red ni de sesión.** Faltan cliente, interceptor de token y almacenamiento de sesión. | `AplicacionResidencia.kt`, carpetas vacías |
| B7 | **No hay navegación por rol.** Las pantallas de administración no son alcanzables: ninguna llamada navega a `admin_lista`. Tampoco existen guardas de ruta. | `Navegacion.kt:96` (solo declaración) |
| B8 | **La URL de release no está definida.** | `app/build.gradle.kts:35` |

---

# 2. Inventario de pantallas y necesidades

| Pantalla o flujo | Ruta frontend | Roles | Datos que muestra | Acciones | Estado actual |
|---|---|---|---|---|---|
| Login | `acceso` (modo LOGIN) | Público | Ninguno del servidor | Iniciar sesión; mostrar/ocultar contraseña; ir a registro; «¿Olvidó su contraseña?» | Solo interfaz. El botón entra sin validar y «Olvidó» no hace nada (`AccesoScreen.kt:116-124`). |
| Registro | `acceso` (modo REGISTRO) | Público | Ninguno | Crear cuenta: nombre completo, correo y contraseña | Solo interfaz. Pasa a «Cuenta creada» sin enviar nada. DTO `RegistroRequest` sin uso. |
| Cuenta creada | `acceso` (modo LISTO) | Público | Texto fijo | «Entrar al mural» (entra sin login); «Volver» | Solo interfaz |
| Muro | `muro` | resident, employee y admin (supuesto; no hay guarda) | Saludo, fecha, primer nombre del usuario, banda de 3 actividades de hoy, filtros y tarjetas de publicación | Filtrar; abrir detalle («Leer aviso» o «Escuchar»); ir a agenda; ir a archivo | Mock (`DatosEjemplo.publicaciones`, `agenda`). Nombre y fecha escritos en el componente (`MuroScreen.kt:49-50`). |
| Agenda «Hoy» | `hoy` | resident, employee y admin (supuesto) | Pestañas Hoy/Mañana/Semana con rango de fechas y actividades (hora, lugar, título, nota) | Cambiar pestaña; «Ver las de hoy» en estado vacío | Mock (`DatosEjemplo.agenda`). Rangos de fecha fijos en el enum (`Modelos.kt:65-69`). |
| Detalle de publicación | `detalle` | resident, employee y admin (supuesto) | Autor, área del autor, fecha, hora, lugar, categoría, «Nuevo», título, cuerpo en 2 párrafos, foto con texto alternativo y ficha Cuándo/Dónde/Publica | Volver; escuchar o detener (TTS local); ver agenda | Datos estáticos en el componente. No recibe ID. |
| Notificaciones | `avisos` | resident (seguro); otros sin definir | Tipo, título, cuerpo, «cuándo» relativo, leída/sin leer y conteo de no leídas | Abrir (marca leída y abre detalle); marcar todas como leídas; archivar las leídas | Mock en memoria (`AppViewModel.kt:75-85`) |
| Insignia de no leídas | Barra inferior | Con sesión | Número de notificaciones sin leer | — | Calculada localmente (`Navegacion.kt:48`) |
| Perfil | `perfil` | resident (datos de residente) | Iniciales, nombre, habitación, año de ingreso, habitación con ala y planta, correo, familiar (nombre, parentesco, teléfono) | Cambiar tamaño de letra; 3 preferencias Sí/No; «Agendar visita familiar»; cerrar sesión | Datos estáticos. Preferencias en memoria. «Agendar visita» sin acción (`PerfilScreen.kt:144`). Cerrar sesión solo navega. |
| Archivo | `archivo` | resident, employee y admin (supuesto) | Meses con entradas: fecha, hora, categoría y título | «Cargar meses anteriores»; volver | Mock (`DatosEjemplo.archivo`). El botón no hace nada (`ArchivoScreen.kt:59`). Las entradas no son tocables. |
| Admin: lista de avisos | `admin_lista` | admin (supuesto por el rótulo «Administración») | Categoría, estado (Fijado/Publicado/Archivado), título, fecha y hora | Editar, Fijar/No fijar, Archivar/Republicar, Eliminar (con confirmación), Deshacer (5 s), Crear aviso nuevo | Mock en memoria. **Inalcanzable desde la navegación.** |
| Admin: crear/editar aviso | `admin_crear` | admin (supuesto) | Formulario y, tras publicar, vista previa | Publicar o guardar cambios; cancelar; adjuntar fotografía (sin acción); ver en el muro; crear otro | Mock en memoria. «Adjuntar» sin acción (`AdminCrearScreen.kt:97`). |
| Gestión de usuarios | — (no hay pantalla) | admin | — | Crear personal o residente, editar, aprobar, deshabilitar | Solo DTOs (`CrearPersonalRequest`, `CrearResidenteRequest`, `EditarUsuarioRequest`, `EditarResidenteRequest`, `UserDto.status`). Sin UI. |
| Cambio de contraseña o PIN | — | Con sesión | — | Cambiar credencial | Solo DTO (`CambioCredencialRequest`). Sin UI. |
| Reacciones | — | — | — | Reaccionar like/love/happy | Solo DTO (`ReaccionesDto`, `ReaccionRequest`). Sin UI. |
| Auditoría | — | admin | — | Consultar bitácora | Solo DTO (`AuditLogDto`). Sin UI. |
| Menús y eventos (formularios específicos) | — | employee/admin | — | Crear o editar menú y evento | Solo DTOs (`CrearMenuRequest`, `CrearEventoRequest`). El formulario de la UI es único y no tiene esos campos. |
| Dashboard o estadísticas | — | — | — | — | **No requerido por el frontend actual** |

---

# 3. Entidades y diccionario de campos

> Los IDs son siempre `string` con dígitos decimales, por ejemplo `"1024"`, nunca `number`. Esta regla está explícita en `Dtos.kt:9-10`: «los IDs son String decimales, nunca Int ni Long».

## 3.1 Usuario (`User`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `id` | `string` | Sí | No | Entero decimal en texto | Sesión, administración | `Dtos.kt:17` | [DTO] |
| `fullName` | `string` | Sí | No | [Propuesta] 2–150 caracteres | Registro, saludo del muro, perfil e iniciales | `Dtos.kt:18`; `AccesoScreen.kt:102`; `MuroScreen.kt:50`; `PerfilScreen.kt:42,45` | El muro muestra el primer nombre («Sebastián»), el perfil dos palabras («Sebastián Ruiz») y las iniciales «SR». Ver la inconsistencia I9. |
| `email` | `string` | Sí | No | Correo válido, [Propuesta] máx. 254 | Login, registro, perfil | `Dtos.kt:19`; `AccesoScreen.kt:105`; `PerfilScreen.kt:96` | [DTO] y UI |
| `role` | `string` | Sí | No | `resident \| employee \| admin` | Navegación por rol (pendiente) | `Dtos.kt:20` | Lo asigna el backend. El registro no lo envía (`Dtos.kt:55`). |
| `status` | `string` | Sí | No | `pending \| active \| disabled` | Aprobación de cuentas | `Dtos.kt:21` | La UI no maneja `pending`: tras registrarse dice «Ya puede entrar». Ver I6. |
| `authMethod` | `string` | Sí | No | `password \| pin` | Login, registro | `Dtos.kt:22` | La UI solo tiene «Contraseña» y no ofrece elegir PIN. Ver I5. |
| `emailVerifiedAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:23` | Sin uso en la UI |
| `approvedBy` | `string` | No | Sí | ID de usuario | — | `Dtos.kt:24` | Sin uso en la UI |
| `approvedAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:25` | Sin uso en la UI |
| `createdAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:26` | Sin uso en la UI |
| `updatedAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:27` | Sin uso en la UI |
| `residentProfile` | `object` (3.2) | No | Sí | — | Perfil | `Dtos.kt:28` | Solo cuando `role = resident` |
| `staffProfile` | `object` (3.3) | No | Sí | — | — | `Dtos.kt:29` | Solo cuando `role` es `employee` o `admin` |

## 3.2 Perfil de residente (`ResidentProfile`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `phone` | `string` | No | Sí | [Propuesta] 10 dígitos MX | Ninguna pantalla | `Dtos.kt:34` | [DTO]. La UI no muestra el teléfono del residente. |
| `department` | `string` | Sí | No | Texto libre | Perfil: «Habitación 112 · Ala sur, planta baja» | `Dtos.kt:35`; `PerfilScreen.kt:46,95` | Ver I10: ¿es la habitación? La UI muestra número, ala y planta. |
| `admissionDate` | `string` | Sí | No | **Solo fecha**, `YYYY-MM-DD` | Perfil: «Desde 2021» | `Dtos.kt:36`; `PerfilScreen.kt:46` | El frontend solo muestra el año |
| `roomNumber` | `string` | [Propuesta] Sí | No | «112» | Perfil | `PerfilScreen.kt:46,95` | [Propuesta] Solo si `department` no es la habitación |
| `roomLocation` | `string` | [Propuesta] No | Sí | «Ala sur, planta baja» | Perfil | `PerfilScreen.kt:95` | [Propuesta] |
| `familyContact` | `object` o `null` | [Propuesta] No | Sí | ver abajo | Perfil: «Familiar» | `PerfilScreen.kt:97` | **No existe en ningún DTO** |
| `familyContact.fullName` | `string` | Sí | No | «Mariana Ruiz» | Perfil | `PerfilScreen.kt:97` | [Propuesta] |
| `familyContact.relationship` | `string` | Sí | No | «hija»; ¿enum o texto? (pregunta 9) | Perfil | `PerfilScreen.kt:97` | [Propuesta] |
| `familyContact.phone` | `string` | Sí | No | «7772148830»; la UI lo muestra como «777 214 88 30» | Perfil | `PerfilScreen.kt:97` | [Propuesta] Solo un contacto visible en la UI |

## 3.3 Perfil de personal (`StaffProfile`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `createdAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:41` | [DTO] Sin uso |
| `lastAccessAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:42` | [DTO] Sin uso |
| `area` | `string` | [Propuesta] No | Sí | «Terapia ocupacional», «Administración» | Detalle y tarjeta del muro | `DetalleScreen.kt:96`; `DatosEjemplo.kt:43` | [Propuesta] La UI muestra el área del autor. Ver 3.5 `author.area`. |

## 3.4 Sesión (`Login`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `user` | `object` (3.1) | Sí | No | — | Toda la app | `Dtos.kt:47` | [DTO] |
| `expiresAt` | `string` | Sí | No | Fecha-hora ISO 8601 con zona | Vencimiento de sesión | `Dtos.kt:48` | [DTO] |
| `tokenType` | `string` | Sí | No | `Bearer` | Header `Authorization` | `Dtos.kt:49` | [DTO] Valor inferido |
| `accessToken` | `string` | Sí | No | Opaco | Header `Authorization` | `Dtos.kt:50` | [DTO] Se devuelve porque `clientType = "android"` (`Dtos.kt:64`) |

## 3.5 Publicación (`Post`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `id` | `string` | Sí | No | Entero decimal en texto | Clave de listas, detalle, acciones | `Dtos.kt:84`; `Modelos.kt:22,52` | La UI usa `"p1"` y `"a1"` en mocks |
| `type` | `string` | Sí | No | `menu \| notice \| event` | Validación de cuerpo | `Dtos.kt:85` | **No es visible en la UI.** Ver B1/I1. |
| `category` | `string` | Sí | No | DTO: `Actividad \| Salud \| Capilla \| Comida`. UI: `AVISO \| ACTIVIDAD \| EVENTO \| NOTICIA` | Etiqueta de la tarjeta, filtros del muro, formulario | `Dtos.kt:86`; `Modelos.kt:6-11` | **Enums incompatibles.** Ver I1. |
| `title` | `string` | Sí | No | [Propuesta] 1–150 caracteres | Tarjeta, detalle, admin, archivo, notificación | `Dtos.kt:87`; `AdminCrearScreen.kt:49` | Obligatorio en la UI (`AppViewModel.kt:177`) |
| `body` | `string` | Sí | No | Texto plano con saltos de línea. [Propuesta] 1–5000 | Tarjeta (completo), detalle (párrafos) | `Dtos.kt:88`; `AdminCrearScreen.kt:79` | Obligatorio en la UI. El detalle lo muestra en 2 párrafos (`DetalleScreen.kt:70-73`). [Propuesta] Separarlos con `\n\n`. |
| `status` | `string` | Sí | No | `draft \| published \| archived` | Admin | `Dtos.kt:89` | La UI no usa `draft` |
| `isPinned` | `boolean` | [Propuesta] Sí | No | — | Borde y píldora «Fijado · Importante» en el muro y en admin | `Modelos.kt:28`; `Modelos.kt:15`; `AdminCrearScreen.kt:87` | **No existe en el DTO.** Ver B2. |
| `author` | `object` | No | Sí | `{ id, fullName }` | Meta de la tarjeta, detalle «Publica» | `Dtos.kt:90,106` | [DTO] |
| `author.id` | `string` | Sí | No | ID | — | `Dtos.kt:106` | |
| `author.fullName` | `string` | Sí | No | — | «Gabriela Popoca» | `DetalleScreen.kt:54` | Los avisos muestran «Administración» (`DatosEjemplo.kt:17`). ¿Se muestra el área en lugar de la persona? (pregunta 6) |
| `author.area` | `string` | [Propuesta] No | Sí | «Terapia ocupacional» | Detalle «Publica», meta del muro | `DetalleScreen.kt:96`; `DatosEjemplo.kt:43` | [Propuesta] No existe en `AutorDto` |
| `menuDate` | `string` | Sí si `type=menu` | Sí en otros tipos | **Solo fecha** `YYYY-MM-DD` | — | `Dtos.kt:91,130` | Sin campo en la UI |
| `mealType` | `string` | Sí si `type=menu` | Sí en otros tipos | `breakfast \| lunch \| dinner \| daily` | — | `Dtos.kt:92,131` | Sin campo en la UI |
| `eventStartAt` | `string` | Sí si `type=event` | Sí en otros tipos | Fecha-hora ISO 8601 con zona | Agenda (hora), detalle «Cuándo», archivo | `Dtos.kt:93`; `HoyScreen.kt:78`; `DetalleScreen.kt:94` | La UI pide Día y Hora como texto libre. Ver B3. |
| `eventEndAt` | `string` | Sí si `type=event` | Sí en otros tipos | Fecha-hora ISO 8601 con zona, ≥ `eventStartAt` | — | `Dtos.kt:94` | La UI nunca muestra la hora de fin |
| `eventLocation` | `string` | Sí si `type=event` | Sí en otros tipos | [Propuesta] 1–120 caracteres | Agenda «lugar», detalle «Dónde», meta | `Dtos.kt:95`; `Modelos.kt:35`; `DetalleScreen.kt:95` | Sin campo en el formulario de admin |
| `publishedAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | Meta de la tarjeta «Hoy a las 8:00 a. m.», fecha en admin, archivo | `Dtos.kt:96`; `Modelos.kt:25,56` | La UI recibe hoy un texto ya formateado (`meta`, `fecha`). Ver I4. |
| `expiresAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:97` | Sin campo en la UI. El cliente envía `null` explícito (`Dtos.kt:221`). |
| `version` | `number` (entero) | Sí | No | ≥ 1 | Concurrencia optimista (supuesto) | `Dtos.kt:98` | Los cuerpos de edición no lo envían. Ver I14. |
| `attachments` | `array<Media>` | Sí | No (vacío `[]`) | — | Foto de la tarjeta y del detalle | `Dtos.kt:99`; `Modelos.kt:27`; `DetalleScreen.kt:75` | La UI muestra una sola imagen |
| `reactions` | `object` (3.7) | Sí | No | — | Ninguna pantalla | `Dtos.kt:100` | [DTO] Sin UI |
| `isRead` | `boolean` | [Propuesta] Sí | No | — | Píldora «Nuevo» | `Modelos.kt:29` (`nueva`); `MuroScreen.kt:216`; `DetalleScreen.kt:64` | Relativo al usuario autenticado. Ver sección 12. |
| `createdAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:101` | |
| `updatedAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:102` | |

**Nombres distintos para el mismo concepto (Post):**

| Concepto | Nombres en el frontend | Recomendado |
|---|---|---|
| Publicación | `Publicacion` (muro), `AvisoAdmin` (admin), `EntradaArchivo` (archivo), `Actividad` (agenda), `PostDto` (red) | `Post` |
| Título | `titulo`, `title` | `title` |
| Cuerpo | `texto`, `body` | `body` |
| Fecha, hora y autor formateados | `meta` (Publicacion, EntradaArchivo), `fecha` (AvisoAdmin), `hora` (Actividad) | `publishedAt` o `eventStartAt` + `author`; se formatea en el cliente |
| Fijado | `fijada` (Boolean), `EstadoAviso.FIJADO`, `fFijar` | `isPinned` |
| Nuevo o no leído | `nueva` | `isRead` (negado) |
| Foto | `foto` (drawable), `attachments` | `attachments` |
| Lugar | `lugar` (Actividad), «Dónde» (Detalle), `eventLocation` | `eventLocation` |
| Nota breve de agenda | `nota` (Actividad) | ¿`body` o un nuevo `summary`? Ver pregunta 5. |

## 3.6 Archivo adjunto (`Media`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `id` | `string` | Sí | No | ID | `attachmentIds` al crear o editar | `Dtos.kt:187` | |
| `postId` | `string` | No | Sí | ID | — | `Dtos.kt:188` | `null` si todavía no se asocia |
| `originalName` | `string` | Sí | No | Nombre de archivo | Compartir PDF | `Dtos.kt:189` | Default `""` |
| `mimeType` | `string` | Sí | No | `image/jpeg \| image/png \| application/pdf` ([Propuesta] lista) | Elegir visor imagen/PDF | `Dtos.kt:190` | |
| `sizeBytes` | `number` (entero) | Sí | No | ≥ 0 | — | `Dtos.kt:191` | Se declara `Long` |
| `altText` | `string` | Sí | No (puede ser `""`) | — | Descripción de accesibilidad de la foto | `Dtos.kt:192`; `DetalleScreen.kt:75` | La UI usa «Residentes sembrando en el patio central» |
| `sortOrder` | `number` (entero) | Sí | No | ≥ 0 | Orden de adjuntos | `Dtos.kt:193` | |
| `contentUrl` | `string` | Sí | No | Ruta **relativa al origen**, p. ej. `/api/v1/media/45/content` | Carga de imagen con Coil autenticado | `Dtos.kt:194-195` | Requiere `Authorization`. Ver sección 10. |
| `createdAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:196` | |

## 3.7 Reacciones (`Reactions`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `counts.like` | `number` (entero) | Sí | No | ≥ 0 | — | `Dtos.kt:209` | [DTO] Sin UI |
| `counts.love` | `number` (entero) | Sí | No | ≥ 0 | — | `Dtos.kt:209` | [DTO] Sin UI |
| `counts.happy` | `number` (entero) | Sí | No | ≥ 0 | — | `Dtos.kt:209` | [DTO] Sin UI |
| `total` | `number` (entero) | Sí | No | Suma de `counts` | — | `Dtos.kt:204` | [DTO] Sin UI |
| `myReaction` | `string` | Sí | Sí | `like \| love \| happy` o `null` | — | `Dtos.kt:205` | [DTO] Relativo al usuario autenticado |

## 3.8 Lectura de publicación (`PostRead`)

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `read` | `boolean` | Sí | No | — | Píldora «Nuevo» | `Dtos.kt:215`; `MuroScreen.kt:216` | [DTO] |
| `readAt` | `string` | Sí | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:215` | `null` si `read = false` |

## 3.9 Notificación (`Notification`) — [Propuesta] completa, sin DTO

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `id` | `string` | Sí | No | ID | Clave de lista, marcar leída | `Modelos.kt:42`; `AvisosScreen.kt:60,70` | |
| `type` | `string` | Sí | No | [Propuesta] `new_post \| reminder` | Píldora «Sin leer · Nuevo aviso» o «Recordatorio» | `Modelos.kt:43`; `AvisosScreen.kt:75` | El mock usa las etiquetas en español «Nuevo aviso» y «Recordatorio». El cliente traduce el enum. |
| `title` | `string` | Sí | No | — | Título en negrita | `Modelos.kt:44` | |
| `body` | `string` | Sí | No | — | Texto secundario | `Modelos.kt:45` (`cuerpo`) | |
| `createdAt` | `string` | Sí | No | Fecha-hora ISO 8601 con zona | «Hace 15 min», «Ayer», «Lunes» | `Modelos.kt:46` (`cuando`) | El mock trae el texto relativo ya calculado. El cliente lo calcula. |
| `isRead` | `boolean` | Sí | No | — | Estilo leída/sin leer, conteo | `Modelos.kt:47` (`leida`) | |
| `readAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | — | [Propuesta] |
| `postId` | `string` | Sí | Sí | ID | Tocar la notificación abre el detalle | `AvisosScreen.kt:70` | **Falta en el modelo**: hoy abre un detalle fijo. `null` si la notificación no apunta a una publicación. |

## 3.10 Actividad de agenda (`AgendaItem`)

Se recomienda que la agenda sea una **vista de `Post` con `type = event`**, no una entidad nueva. Se confirma en la pregunta 4.

| Campo UI | Tipo esperado | Obligatorio | Puede ser null | Formato | Evidencia | Mapeo recomendado |
|---|---|---:|---:|---|---|---|
| `hora` | `string` | Sí | No | «10:30 a. m.»; en la pestaña Semana, «Viernes, 10:00 a. m.» | `Modelos.kt:34`; `DatosEjemplo.kt:50,58` | `eventStartAt`, formateado en el cliente |
| `lugar` | `string` | Sí | No | «Patio central» | `Modelos.kt:35` | `eventLocation` |
| `titulo` | `string` | Sí | No | — | `Modelos.kt:36` | `title` |
| `nota` | `string` | Sí | No | Una línea | `Modelos.kt:37` | ¿`body` completo o `summary` nuevo? (pregunta 5) |
| — (ID) | `string` | Sí | No | — | La banda del muro abre el detalle (`MuroScreen.kt:102`) | `id`. **Falta en el modelo `Actividad`.** |

## 3.11 Preferencias del usuario (`Preferences`) — [Propuesta]

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `notifyNotices` | `boolean` | Sí | No | Default `true` | «Avisos de la administración» | `AppViewModel.kt:24`; `PerfilScreen.kt:119` | Filtra notificaciones en el servidor |
| `notifyActivities` | `boolean` | Sí | No | Default `true` | «Actividades y talleres» | `AppViewModel.kt:25`; `PerfilScreen.kt:120` | |
| `notifyPhotos` | `boolean` | Sí | No | Default `false` | «Fotografías de los eventos» | `AppViewModel.kt:26`; `PerfilScreen.kt:121` | No existe un tipo «fotografías» en el contrato. Ver I2. |
| `textScale` | `string` | No | No | `small \| normal \| large` | Tamaño de letra A−/A/A+ | `Modelos.kt:72-76` | **Puede quedarse solo en el dispositivo.** Ver pregunta 10. |

## 3.12 Bitácora de auditoría (`AuditLog`) — [DTO], sin UI

| Campo | Tipo esperado | Obligatorio | Puede ser null | Formato o enum | Dónde se usa | Evidencia en frontend | Observaciones |
|---|---|---:|---:|---|---|---|---|
| `id` | `string` | Sí | No | ID | — | `Dtos.kt:268` | |
| `actorId` | `string` | No | Sí | ID de usuario | — | `Dtos.kt:269` | |
| `action` | `string` | Sí | No | Sin enum definido | — | `Dtos.kt:270` | Pregunta 15 |
| `entityType` | `string` | No | Sí | Sin enum definido | — | `Dtos.kt:271` | |
| `entityId` | `string` | No | Sí | ID | — | `Dtos.kt:272` | |
| `requestId` | `string` | No | Sí | — | — | `Dtos.kt:273` | |
| `createdAt` | `string` | No | Sí | Fecha-hora ISO 8601 con zona | — | `Dtos.kt:274` | |

## 3.13 Visita familiar — sin definir

El botón «AGENDAR VISITA FAMILIAR» existe (`PerfilScreen.kt:140-147`) con `clickable { }` vacío. No hay formulario, modelo ni DTO. **No se pueden enumerar campos a partir del código.** Ver pregunta 11.

## 3.14 Formatos de fecha

| Tipo | Formato | Campos |
|---|---|---|
| Solo día | `YYYY-MM-DD`, sin hora ni zona | `admissionDate`, `menuDate` |
| Fecha y hora con zona | ISO 8601 con desfase, p. ej. `2026-09-16T10:30:00-06:00` o `2026-09-16T16:30:00Z` | Todos los demás `*At` |

[Propuesta] La zona de negocio es `America/Mexico_City` (Cuernavaca, UTC−6 sin horario de verano). Define «hoy», «mañana» y «esta semana» en la agenda. Ver pregunta 3.

---

# 4. Requerimientos de autenticación y sesión

## 4.1 Campos de los formularios

**Login** (`AccesoScreen.kt:105-108`):

| Campo UI | Campo API | Tipo | Obligatorio | Validación |
|---|---|---|---:|---|
| «Correo electrónico» | `email` | `string` | Sí | Correo válido. Hoy no hay validación en la UI. |
| «Contraseña» | `secret` | `string` | Sí | [Propuesta] Mín. 8 si `password`; 4–6 dígitos si `pin`. Hoy no hay validación. |
| (no visible) | `authMethod` | `string` | Sí | `password \| pin`. La UI no lo pide y enviaría `"password"`. |
| (no visible) | `clientType` | `string` | Sí | Siempre `"android"` (`Dtos.kt:64-70`) |

**Registro** (`AccesoScreen.kt:101-108`):

| Campo UI | Campo API | Tipo | Obligatorio | Validación |
|---|---|---|---:|---|
| «Nombre completo» | `fullName` | `string` | Sí | [Propuesta] 2–150 caracteres, sin espacios al inicio o al final |
| «Correo electrónico» | `email` | `string` | Sí | Correo válido y único |
| «Contraseña» | `secret` | `string` | Sí | Igual que en login |
| (no visible) | `authMethod` | `string` | Sí | `password \| pin` |

El registro **no envía** `role`, `status` ni perfiles (`Dtos.kt:55`: «Nada de role, status ni perfiles»).

## 4.2 Token y sesión

- **Mecanismo** [DTO]: token Bearer en el cuerpo del login (`accessToken`, `tokenType`, `expiresAt`). `clientType: "web"` devolvería una cookie (`Dtos.kt:64`). Android siempre usa token.
- **Header**: `Authorization: Bearer <accessToken>` en todas las peticiones autenticadas, incluidas las imágenes y PDF que carga Coil (`app/build.gradle.kts:84-86`).
- **Almacenamiento local previsto**: SharedPreferences `sesion_residencia.xml`, excluido de respaldo y transferencia (`res/xml/reglas_respaldo.xml`).
- **Refresh token**: no aparece en el contrato. [Propuesta] No requerido. Al vencer, el usuario vuelve a iniciar sesión. Ver pregunta 2.

## 4.3 Recarga de la app y vencimiento

Hoy la app siempre arranca en `acceso` (`Navegacion.kt:69`) y no persiste nada. Comportamiento requerido [Propuesta]:

1. Al abrir, si hay token guardado y `expiresAt` es futuro, llamar a `GET /auth/me`.
2. Con `200`, actualizar el usuario y entrar al muro, o a la lista de administración si es admin (pregunta 1).
3. Con `401`, borrar la sesión local e ir a `acceso`.
4. Si `expiresAt` ya pasó, borrar la sesión sin llamar a la red.

## 4.4 Cerrar sesión

Hoy solo navega a `acceso` (`Navegacion.kt:91-93`). Requerido [Propuesta]: `POST /auth/logout` invalida el token en el servidor. El cliente borra `sesion_residencia.xml` y la caché de adjuntos (`cache/adjuntos/`, `res/xml/rutas_archivos.xml`) aunque la llamada falle.

## 4.5 Rutas protegidas por rol

**Encontrado: no existe ninguna guarda.** Todas las rutas son accesibles tras pulsar «Iniciar sesión». Las de administración no tienen enlace. Requerido [Propuesta]:

| Ruta | Público | resident | employee | admin |
|---|:-:|:-:|:-:|:-:|
| `acceso` | ✓ | — | — | — |
| `muro`, `hoy`, `detalle`, `avisos`, `archivo` | — | ✓ | ✓ | ✓ |
| `perfil` | — | ✓ | ✓ (sin datos de residente) | ✓ (sin datos de residente) |
| `admin_lista`, `admin_crear` | — | — | ? (pregunta 7) | ✓ |

## 4.6 Redirecciones `401` y `403`

| Estado | Comportamiento esperado del frontend [Propuesta] |
|---|---|
| `401` en cualquier endpoint salvo `/auth/login` | Borrar la sesión e ir a `acceso` con el mensaje «Su sesión terminó. Vuelva a entrar.» |
| `401` en `/auth/login` | Mostrar `Consejo` bajo el formulario, como en `AccesoScreen.kt:110` |
| `403` | Permanecer en la pantalla y mostrar `error.message` en el aviso emergente (`AvisoEmergente.kt`). En rutas de admin, volver al muro. |

## 4.7 Recuperación y cambio de contraseña

- **Recuperación**: el enlace «¿Olvidó su contraseña?» existe pero no hace nada (`AccesoScreen.kt:124`). El texto de la pantalla dice «Si no la recuerda, recepción puede reponerla» (`AccesoScreen.kt:110`). Eso sugiere reposición manual por un admin, no un flujo por correo. Ver pregunta 12.
- **Cambio de credencial**: hay DTO `CambioCredencialRequest { currentSecret, authMethod, secret }` (`Dtos.kt:73-78`) pero no hay UI.

## 4.8 JSON exactos

**Registro — request** (`POST /auth/register`):

```json
{
  "fullName": "Sebastián Ruiz Mendoza",
  "email": "sebastian.ruiz@correo.com",
  "authMethod": "password",
  "secret": "Jardin2026"
}
```

**Registro — respuesta `201`**:

```json
{
  "data": {
    "id": "1024",
    "fullName": "Sebastián Ruiz Mendoza",
    "email": "sebastian.ruiz@correo.com",
    "role": "resident",
    "status": "pending",
    "authMethod": "password",
    "emailVerifiedAt": null,
    "approvedBy": null,
    "approvedAt": null,
    "createdAt": "2026-09-16T15:02:11Z",
    "updatedAt": "2026-09-16T15:02:11Z",
    "residentProfile": null,
    "staffProfile": null
  }
}
```

**Login — request** (`POST /auth/login`):

```json
{
  "email": "sebastian.ruiz@correo.com",
  "authMethod": "password",
  "secret": "Jardin2026",
  "clientType": "android"
}
```

**Login — respuesta `200`**:

```json
{
  "data": {
    "user": {
      "id": "1024",
      "fullName": "Sebastián Ruiz Mendoza",
      "email": "sebastian.ruiz@correo.com",
      "role": "resident",
      "status": "active",
      "authMethod": "password",
      "emailVerifiedAt": "2026-09-10T17:20:00Z",
      "approvedBy": "3",
      "approvedAt": "2026-09-11T14:00:00Z",
      "createdAt": "2026-09-10T17:15:42Z",
      "updatedAt": "2026-09-11T14:00:00Z",
      "residentProfile": {
        "phone": null,
        "department": "112",
        "admissionDate": "2021-03-01"
      },
      "staffProfile": null
    },
    "expiresAt": "2026-09-23T15:05:00Z",
    "tokenType": "Bearer",
    "accessToken": "rg_at_7f3c9b2e41d84a0f9c1e6b5a2d7e8f10"
  }
}
```

**Sesión actual — respuesta `200`** (`GET /auth/me`): tiene la misma forma que `data.user` del login, envuelta en `{ "data": User }`. Si se aprueban las propuestas 3.2 y 3.11, también incluye `residentProfile.familyContact` y `preferences`. El ejemplo completo está en la sección 7.2.

**Error de credenciales `401`**:

```json
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "El correo o la contraseña no coinciden.",
    "requestId": "req_01J8ZK3M5Q",
    "details": null
  }
}
```

**Cuenta pendiente de aprobación `403`** [Propuesta]:

```json
{
  "error": {
    "code": "ACCOUNT_PENDING",
    "message": "Recepción todavía no confirma su cuenta.",
    "requestId": "req_01J8ZK3P2A",
    "details": null
  }
}
```

**Cuenta deshabilitada `403`** [Propuesta]:

```json
{
  "error": {
    "code": "ACCOUNT_DISABLED",
    "message": "Su cuenta está desactivada. Acuda a recepción.",
    "requestId": "req_01J8ZK3R7B",
    "details": null
  }
}
```

**Validación de registro `422`**:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Revise los datos marcados.",
    "requestId": "req_01J8ZK3T9C",
    "details": [
      { "field": "email", "message": "Ya existe una cuenta con este correo." },
      { "field": "secret", "message": "La contraseña debe tener al menos 8 caracteres." }
    ]
  }
}
```

---

# 5. Matriz de permisos

> **Evidencia:** el código no contiene ninguna condición por rol (`grep` de `role` fuera de `Dtos.kt` sin resultados). Las marcas se basan en el texto visible de cada pantalla («Administración», «Su perfil», «Habitación») y en los comentarios de los DTOs. Toda la matriz es **[Propuesta] a confirmar**. «?» indica una decisión pendiente.

| Recurso o acción | Público | resident | employee | admin | Evidencia o pantalla |
|---|:-:|:-:|:-:|:-:|---|
| Registrarse | ✓ | — | — | — | `AccesoScreen.kt` modo REGISTRO |
| Iniciar sesión | ✓ | — | — | — | `AccesoScreen.kt` modo LOGIN |
| Ver sesión actual / cerrar sesión | — | ✓ | ✓ | ✓ | `PerfilScreen.kt:150` |
| Listar publicaciones publicadas en el muro | — | ✓ | ✓ | ✓ | `MuroScreen.kt` |
| Ver detalle de publicación publicada | — | ✓ | ✓ | ✓ | `DetalleScreen.kt` |
| Ver publicaciones archivadas en el archivo del residente | — | ? | ? | ✓ | `ArchivoScreen.kt` (pregunta 8) |
| Ver agenda | — | ✓ | ✓ | ✓ | `HoyScreen.kt` |
| Marcar publicación como leída | — | ✓ | ? | ? | Píldora «Nuevo» |
| Reaccionar | — | ✓ | ? | ? | Solo DTO |
| Listar, marcar y archivar notificaciones propias | — | ✓ | ? | ? | `AvisosScreen.kt` |
| Ver perfil propio | — | ✓ | ✓ | ✓ | `PerfilScreen.kt` |
| Editar nombre propio | — | ? | ? | ? | DTO `NombreRequest` (`Dtos.kt:264`) |
| Cambiar credencial propia | — | ✓ | ✓ | ✓ | DTO `CambioCredencialRequest` |
| Guardar preferencias propias | — | ✓ | ? | ? | `PerfilScreen.kt:110-134` |
| Solicitar visita familiar | — | ✓ | — | — | `PerfilScreen.kt:140` (pregunta 11) |
| Listar publicaciones en todos los estados (admin) | — | — | ? | ✓ | `AdminListaScreen.kt` |
| Crear o editar aviso (`notice`) | — | — | ? | ✓ | `AdminCrearScreen.kt` |
| Crear o editar menú (`menu`) | — | — | ✓ (cocina) | ✓ | Solo DTO `CrearMenuRequest` (pregunta 7) |
| Crear o editar evento (`event`) | — | — | ? | ✓ | Solo DTO `CrearEventoRequest` |
| Editar publicaciones de otro autor | — | — | ✗ (propuesta) | ✓ | — |
| Fijar / desfijar | — | — | ? | ✓ | `AdminListaScreen.kt:126` |
| Archivar / republicar | — | — | ? | ✓ | `AdminListaScreen.kt:127` |
| Eliminar publicación | — | — | ✗ | ✓ | `AdminListaScreen.kt:137-141` |
| Subir o eliminar adjuntos | — | — | ? | ✓ | `AdminCrearScreen.kt:93-98` |
| Descargar adjuntos | — | ✓ | ✓ | ✓ | `Dtos.kt:194`, `rutas_archivos.xml` |
| Gestionar usuarios (listar, crear, editar, aprobar, deshabilitar) | — | — | — | ✓ | DTOs `CrearPersonalRequest` y otros |
| Consultar auditoría | — | — | — | ✓ | DTO `AuditLogDto` |

---

# 6. Endpoints requeridos

Formatos comunes (`Envolturas.kt:6-14`):

- Objeto único: `{ "data": { } }`
- Lista paginada: `{ "data": [ ], "meta": { "page": 1, "limit": 20, "total": 0 } }`
- Error: `{ "error": { "code", "message", "requestId", "details" } }`
- `204` sin cuerpo

Todos los endpoints salvo `register`, `login` y `health` requieren `Authorization: Bearer <token>`.

---

## `POST /auth/register`

**Propósito:** crear una cuenta de residente desde la app.

**Pantallas que lo consumen:** Acceso (modo REGISTRO).

**Roles autorizados:** Público.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Content-Type: application/json`.

**Body enviado por el frontend:**

```json
{
  "fullName": "Sebastián Ruiz Mendoza",
  "email": "sebastian.ruiz@correo.com",
  "authMethod": "password",
  "secret": "Jardin2026"
}
```

**Validaciones necesarias:**

- `fullName`: obligatorio, `string`, se recortan espacios, 2–150 caracteres [Propuesta].
- `email`: obligatorio, formato de correo, sin distinguir mayúsculas y minúsculas, único → `409` si ya existe.
- `authMethod`: obligatorio, `password | pin`.
- `secret`: obligatorio. Con `password`, mín. 8 caracteres [Propuesta]. Con `pin`, 4–6 dígitos [Propuesta].
- Cualquier campo adicional (`role`, `status`, `residentProfile`) → `422` [Propuesta].

**Respuesta exitosa esperada:** `201` con `{ "data": User }` (ejemplo en la sección 4.8). No devuelve token.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 201 | Cuenta creada | Mostrar «Cuenta creada». Si `status = pending`, el texto debe decir que falta la confirmación de recepción (I6). |
| 409 | Correo ya registrado | Mostrar el mensaje en `Consejo` |
| 422 | Validación | Mostrar `details` por campo |
| 429 | Demasiados intentos | Mostrar mensaje genérico |
| 500/503 | Error del servidor | Mostrar «Inténtelo más tarde» |

**Reglas de negocio:**

- `role` siempre `resident`. `status` inicial `pending` según el DTO. Se confirma en la pregunta 13.
- `residentProfile` queda `null` hasta que recepción lo complete: «Recepción confirma su habitación» (`AccesoScreen.kt:95`).
- No es idempotente: un segundo intento con el mismo correo devuelve `409`.

**Evidencia en el frontend:** `AccesoScreen.kt:95-122`; `Dtos.kt:55-62`.

---

## `POST /auth/login`

**Propósito:** autenticar y obtener el token.

**Pantallas que lo consumen:** Acceso (modo LOGIN).

**Roles autorizados:** Público.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Content-Type: application/json`.

**Body enviado por el frontend:**

```json
{
  "email": "sebastian.ruiz@correo.com",
  "authMethod": "password",
  "secret": "Jardin2026",
  "clientType": "android"
}
```

**Validaciones necesarias:**

- `email`: obligatorio, formato de correo.
- `authMethod`: obligatorio, `password | pin`. Debe coincidir con el método de la cuenta.
- `secret`: obligatorio, no vacío.
- `clientType`: obligatorio, `android | web`. Con `android` se devuelve el token en el cuerpo.

**Respuesta exitosa esperada:** `200` con `{ "data": { "user", "expiresAt", "tokenType", "accessToken" } }` (sección 4.8).

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Credenciales válidas y cuenta `active` | Guardar la sesión y navegar según el rol |
| 401 | Credenciales incorrectas | `Consejo`: «Revise su correo y su contraseña…» |
| 403 | Cuenta `pending` o `disabled` | Mostrar `error.message` |
| 422 | Campos faltantes | Mostrar `details` |
| 429 | Bloqueo por intentos | Mostrar mensaje y no reintentar automáticamente |

**Reglas de negocio:**

- No revelar si el correo existe: usar el mismo `401` para correo inexistente y contraseña incorrecta.
- Limitar la frecuencia de intentos por correo e IP.

**Evidencia en el frontend:** `AccesoScreen.kt:105-118`; `Dtos.kt:46-51,64-71`.

---

## `GET /auth/me`

**Propósito:** restaurar la sesión al abrir la app y obtener los datos del perfil.

**Pantallas que lo consumen:** arranque de la app, Muro (primer nombre), Perfil.

**Roles autorizados:** resident, employee, admin.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** token válido y vigente.

**Respuesta exitosa esperada:** `200` con `{ "data": User }`. Ejemplo en la sección 7.2 con los campos [Propuesta] `familyContact` y `preferences`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Sesión válida | Entrar a la app |
| 401 | Token vencido o revocado | Borrar la sesión e ir a `acceso` |
| 403 | Cuenta deshabilitada después del login | Borrar la sesión y mostrar el mensaje |

**Reglas de negocio:** devuelve solo al usuario del token.

**Evidencia en el frontend:** `MuroScreen.kt:49-50`; `PerfilScreen.kt:42-46,94-98`; `Navegacion.kt:69`.

---

## `POST /auth/logout`

**Propósito:** revocar el token actual.

**Pantallas que lo consumen:** Perfil («Cerrar sesión»).

**Roles autorizados:** resident, employee, admin.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna.

**Respuesta exitosa esperada:** `204` sin cuerpo.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 204 | Token revocado | Borrar la sesión local e ir a `acceso` |
| 401 | Token ya inválido | Igual que 204 |

**Reglas de negocio:** idempotente.

**Evidencia en el frontend:** `PerfilScreen.kt:150`; `Navegacion.kt:91-93`.

---

## `PUT /auth/me/credentials`

**Propósito:** cambiar la contraseña o el PIN propios.

**Pantallas que lo consumen:** ninguna todavía. Solo existe el DTO.

**Roles autorizados:** resident, employee, admin.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:**

```json
{
  "currentSecret": "Jardin2026",
  "authMethod": "pin",
  "secret": "4821"
}
```

**Validaciones necesarias:**

- `currentSecret`: obligatorio, debe coincidir → `422` en `field: "currentSecret"` si no.
- `authMethod`: `password | pin`.
- `secret`: mismas reglas que en el registro.

**Respuesta exitosa esperada:** `204`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 204 | Cambiado | Confirmar con el aviso emergente |
| 422 | Credencial actual incorrecta o nueva inválida | Mostrar el error del campo |
| 401 | Sesión vencida | Ir a `acceso` |

**Reglas de negocio:** [Propuesta] revocar los demás tokens del usuario.

**Evidencia en el frontend:** `Dtos.kt:73-78`.

---

## `PATCH /auth/me`

**Propósito:** editar el nombre propio.

**Pantallas que lo consumen:** ninguna. Solo existe el DTO `NombreRequest`.

**Roles autorizados:** a confirmar (pregunta 14).

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:**

```json
{
  "fullName": "Sebastián Ruiz Mendoza"
}
```

**Validaciones necesarias:** `fullName`: obligatorio, 2–150 caracteres.

**Respuesta exitosa esperada:** `200` con `{ "data": User }`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Actualizado | Refrescar el perfil |
| 422 | Nombre inválido | Error del campo |

**Reglas de negocio:** solo el usuario del token.

**Evidencia en el frontend:** `Dtos.kt:263-264`.

---

## `GET /auth/me/preferences` y `PUT /auth/me/preferences` — [Propuesta]

**Propósito:** leer y guardar las preferencias de notificación.

**Pantallas que lo consumen:** Perfil («Avisos que quiero recibir»).

**Roles autorizados:** resident (employee y admin a confirmar).

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend (PUT):**

```json
{
  "notifyNotices": true,
  "notifyActivities": true,
  "notifyPhotos": false
}
```

**Validaciones necesarias:** los tres campos son obligatorios y `boolean`.

**Respuesta exitosa esperada (GET y PUT):**

```json
{
  "data": {
    "notifyNotices": true,
    "notifyActivities": true,
    "notifyPhotos": false
  }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Leído o guardado | Reflejar el interruptor |
| 422 | Tipo inválido | Revertir el interruptor y mostrar el aviso emergente |

**Reglas de negocio:** el backend usa estas preferencias al generar notificaciones. PUT es idempotente. Alternativa: incluirlas en `GET /auth/me` (sección 12).

**Evidencia en el frontend:** `AppViewModel.kt:24-35`; `PerfilScreen.kt:110-134`.

---

## `GET /posts`

**Propósito:** listar publicaciones para el Muro, el Archivo y la lista de Administración. La agenda también lo usa si se aprueba la pregunta 4.

**Pantallas que lo consumen:** Muro, Archivo, Hoy (agenda), Admin lista.

**Roles autorizados:** resident, employee, admin. `status` distinto de `published` solo para admin (y employee según la pregunta 7).

**Parámetros de ruta:** ninguno.

**Query parameters:**

| Nombre | Tipo | Obligatorio | Default | Valores permitidos | Descripción |
|---|---|---:|---|---|---|
| `page` | `number` | No | `1` | ≥ 1 | Página (`MetaDto.page`) |
| `limit` | `number` | No | `20` | 1–100 | Tamaño de página (`MetaDto.limit`) |
| `status` | `string` | No | `published` | `draft \| published \| archived \| all` | `all` solo para admin (lista de administración) |
| `type` | `string` | No | — | `menu \| notice \| event` | Filtro por tipo |
| `category` | `string` | No | — | Según la resolución de la pregunta 16 | Filtros del muro: Avisos, Actividades, Eventos, Noticias |
| `hasImage` | `boolean` | No | — | `true` | [Propuesta] Filtro «Fotografías» (`MuroScreen.kt:33`) |
| `pinned` | `boolean` | No | — | `true \| false` | [Propuesta] |
| `eventFrom` | `string` | No | — | Fecha-hora ISO 8601 con zona | Agenda: inicio del rango de `eventStartAt` (inclusivo) |
| `eventTo` | `string` | No | — | Fecha-hora ISO 8601 con zona | Agenda: fin del rango (exclusivo) |
| `publishedBefore` | `string` | No | — | Fecha-hora ISO 8601 con zona | Archivo: publicaciones anteriores a esta fecha |
| `sort` | `string` | No | `-pinned,-publishedAt` | `-pinned,-publishedAt \| -publishedAt \| eventStartAt` | Muro: fijadas primero. Agenda: por hora de inicio. Admin: `-publishedAt`. |

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:**

- `page` y `limit`: enteros dentro del rango. Si no → `422`.
- `status` distinto de `published` sin permiso → `403`.
- `eventFrom` < `eventTo`.

**Respuesta exitosa esperada:**

```json
{
  "data": [
    {
      "id": "501",
      "type": "notice",
      "category": "Aviso",
      "title": "Suspensión temporal del agua caliente",
      "body": "Los calentadores del ala sur se revisan de 9:00 a 12:00. Durante esas horas habrá agua fría en los baños de las habitaciones 100 a 130.",
      "status": "published",
      "isPinned": true,
      "isRead": false,
      "author": { "id": "3", "fullName": "Administración", "area": "Administración" },
      "menuDate": null,
      "mealType": null,
      "eventStartAt": null,
      "eventEndAt": null,
      "eventLocation": null,
      "publishedAt": "2026-09-16T14:00:00Z",
      "expiresAt": null,
      "version": 1,
      "attachments": [],
      "reactions": { "counts": { "like": 4, "love": 1, "happy": 0 }, "total": 5, "myReaction": null },
      "createdAt": "2026-09-16T13:58:40Z",
      "updatedAt": "2026-09-16T14:00:00Z"
    },
    {
      "id": "502",
      "type": "event",
      "category": "Actividad",
      "title": "Tarde de jardinería comunitaria",
      "body": "Les invitamos a participar en la siembra de nuevas plantas florales y aromáticas en el patio central.\n\nHabrá café, música suave y acompañamiento del equipo de terapia ocupacional.",
      "status": "published",
      "isPinned": false,
      "isRead": false,
      "author": { "id": "17", "fullName": "Gabriela Popoca", "area": "Terapia ocupacional" },
      "menuDate": null,
      "mealType": null,
      "eventStartAt": "2026-09-16T16:30:00Z",
      "eventEndAt": "2026-09-16T18:00:00Z",
      "eventLocation": "Patio central, junto a la fuente",
      "publishedAt": "2026-09-15T20:10:00Z",
      "expiresAt": "2026-09-16T18:00:00Z",
      "version": 2,
      "attachments": [
        {
          "id": "45",
          "postId": "502",
          "originalName": "jardineria.jpg",
          "mimeType": "image/jpeg",
          "sizeBytes": 348221,
          "altText": "Residentes sembrando en el patio central",
          "sortOrder": 0,
          "contentUrl": "/api/v1/media/45/content",
          "createdAt": "2026-09-15T20:05:12Z"
        }
      ],
      "reactions": { "counts": { "like": 9, "love": 6, "happy": 3 }, "total": 18, "myReaction": "love" },
      "createdAt": "2026-09-15T20:04:00Z",
      "updatedAt": "2026-09-15T21:30:00Z"
    }
  ],
  "meta": { "page": 1, "limit": 20, "total": 2 }
}
```

> `category: "Aviso"` y `"Actividad"` son ilustrativos hasta resolver la pregunta 16.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Siempre que la consulta sea válida, aunque `data` venga vacío | Tarjetas, o «No hay nada en este filtro» (`MuroScreen.kt:192`), «No hay actividades programadas» (`HoyScreen.kt:67`) o «No hay avisos publicados» (`AdminListaScreen.kt:50`) |
| 401 | Sesión vencida | Ir a `acceso` |
| 403 | `status` no permitido para el rol | Volver al muro |
| 422 | Parámetros inválidos | Error genérico |

**Reglas de negocio:**

- Sin permiso de administración, solo `status = published` y no vencidas (`expiresAt` nulo o futuro). Ver la pregunta 17 sobre vencidas en el archivo.
- `isRead` y `reactions.myReaction` son relativos al usuario del token.
- Orden por defecto: fijadas primero y luego por `publishedAt` descendente. En el mock, la fijada aparece primero (`DatosEjemplo.kt:14-20`).

**Evidencia en el frontend:** `AppViewModel.kt:58-60`; `MuroScreen.kt:32-33,61-77`; `HoyScreen.kt:57-85`; `ArchivoScreen.kt:40-61`; `AdminListaScreen.kt:45-66`; `Envolturas.kt:18-22`.

---

## `GET /posts/{postId}`

**Propósito:** obtener una publicación para la pantalla de Detalle y para precargar el formulario de edición.

**Pantallas que lo consumen:** Detalle; Admin crear/editar.

**Roles autorizados:** resident, employee, admin. No publicadas: solo admin (y employee autor, según la pregunta 7).

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `postId` | `string` | Sí | `"502"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** `postId` numérico en texto.

**Respuesta exitosa esperada:** `200` con `{ "data": Post }`, con la misma forma que un elemento de `GET /posts`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Encontrada | Mostrar el detalle |
| 403 | No publicada y sin permiso | Volver al muro con mensaje |
| 404 | No existe, fue eliminada o está oculta al rol | Volver al muro con «Este aviso ya no está disponible» |

**Reglas de negocio:** [Propuesta] abrir el detalle no marca la lectura automáticamente. El cliente llama a `PUT /posts/{postId}/read`. Ver pregunta 18.

**Evidencia en el frontend:** `DetalleScreen.kt:32-117`; `AppViewModel.kt:163-174`.

---

## `POST /posts`

**Propósito:** crear una publicación: aviso, menú o evento.

**Pantallas que lo consumen:** Admin crear («Publicar aviso»).

**Roles autorizados:** admin; employee a confirmar (pregunta 7).

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`, `Content-Type: application/json`. [Propuesta] `Idempotency-Key: <uuid>` para evitar duplicados si se toca «Publicar» dos veces.

**Body enviado por el frontend** (uno de tres, según `type`; `Dtos.kt:108-145`).

Aviso:

```json
{
  "type": "notice",
  "category": "Aviso",
  "title": "Menú especial de domingo",
  "body": "Este domingo habrá comida familiar en la terraza a partir de la 1:00 p. m.",
  "expiresAt": null,
  "attachmentIds": [],
  "isPinned": false
}
```

Menú:

```json
{
  "type": "menu",
  "category": "Comida",
  "title": "Comida del domingo 20",
  "body": "Sopa de fideo, pollo en mole con arroz y agua de jamaica.",
  "expiresAt": "2026-09-20T23:59:00-06:00",
  "attachmentIds": [],
  "menuDate": "2026-09-20",
  "mealType": "lunch",
  "isPinned": false
}
```

Evento:

```json
{
  "type": "event",
  "category": "Actividad",
  "title": "Taller de acuarela",
  "body": "Quedan cuatro lugares. Traiga ropa que se pueda manchar.",
  "expiresAt": null,
  "attachmentIds": ["45"],
  "eventStartAt": "2026-09-19T11:00:00-06:00",
  "eventEndAt": "2026-09-19T12:30:00-06:00",
  "eventLocation": "Salón de usos múltiples",
  "isPinned": false
}
```

> `isPinned` es [Propuesta] (B2). El cliente serializa `null` explícitos (`Dtos.kt:221`), así que el backend debe aceptar `"expiresAt": null`.

**Validaciones necesarias:**

- `type`: obligatorio, `notice | menu | event`.
- `category`: obligatorio, dentro del enum acordado (pregunta 16).
- `title`: obligatorio, sin espacios al inicio o al final, 1–150 caracteres [Propuesta]. La UI exige que no esté vacío (`AppViewModel.kt:177`).
- `body`: obligatorio, 1–5000 caracteres [Propuesta]. La UI exige que no esté vacío.
- `expiresAt`: opcional o `null`. Si viene, debe ser futuro.
- `attachmentIds`: `array<string>`, puede ser vacío. Cada ID debe existir, ser del autor y no estar asociado a otra publicación.
- `menuDate` y `mealType`: obligatorios solo si `type = menu`. Si vienen en otro tipo → `422` (`Dtos.kt:109-110`).
- `eventStartAt`, `eventEndAt` y `eventLocation`: obligatorios solo si `type = event`, con `eventEndAt ≥ eventStartAt`. Si vienen en otro tipo → `422`.
- `isPinned` [Propuesta]: `boolean`, default `false`.

**Respuesta exitosa esperada:** `201` con `{ "data": Post }` y `status = "published"`. La pantalla de confirmación usa `title`, `category`, `isPinned`, `publishedAt`, `body` y `author` (`AdminCrearScreen.kt:112-135`).

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 201 | Creada | Mostrar «El aviso ya está en el muro» |
| 403 | Rol sin permiso para ese `type` | Mensaje en `Consejo` |
| 409 | `Idempotency-Key` repetida con otro cuerpo | Mensaje genérico |
| 422 | Validación | `Consejo` con el error. Hoy: «Falta el título o el texto del aviso…» (`AppViewModel.kt:178`) |

**Reglas de negocio:**

- La UI no ofrece borradores: se crea con `status = published` y `publishedAt = now`. Ver pregunta 19.
- **Al publicar, se generan notificaciones para los residentes.** La confirmación dice «les llegó a los residentes como notificación» (`AdminCrearScreen.kt:115`). Deben respetarse las preferencias de 3.11.
- `author` = usuario del token.

**Evidencia en el frontend:** `AdminCrearScreen.kt:44-110`; `AppViewModel.kt:176-193`; `Dtos.kt:108-145`.

---

## `PUT /posts/{postId}`

**Propósito:** reemplazar el contenido de una publicación existente.

**Pantallas que lo consumen:** Admin editar («Guardar cambios»).

**Roles autorizados:** admin; employee autor a confirmar.

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `postId` | `string` | Sí | `"501"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`. [Propuesta] `If-Match: "<version>"` para concurrencia optimista (I14).

**Body enviado por el frontend** (sin `type`: el tipo no puede cambiar, `Dtos.kt:147`). Ejemplo de aviso:

```json
{
  "category": "Aviso",
  "title": "Suspensión temporal del agua caliente",
  "body": "Los calentadores del ala sur se revisan de 9:00 a 13:00. Durante esas horas habrá agua fría en los baños de las habitaciones 100 a 130.",
  "expiresAt": null,
  "attachmentIds": [],
  "isPinned": true
}
```

Para menú se agregan `menuDate` y `mealType`. Para evento, `eventStartAt`, `eventEndAt` y `eventLocation`, como en `POST /posts`.

**Validaciones necesarias:** las mismas que en `POST /posts`, según el `type` almacenado. Si el cuerpo trae `type` → `422`. Los adjuntos que no estén en `attachmentIds` se desasocian.

**Respuesta exitosa esperada:** `200` con `{ "data": Post }` y `version` incrementado.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Guardada | Pantalla de confirmación |
| 403 | Sin permiso | Mensaje |
| 404 | No existe | Volver a la lista |
| 409 | `version` desactualizada | «Otra persona modificó este aviso. Recargue.» |
| 422 | Validación | `Consejo` |

**Reglas de negocio:** reemplazo completo (PUT). No cambia `status` ni `publishedAt`. [Propuesta] No vuelve a notificar a los residentes (pregunta 20).

**Evidencia en el frontend:** `AppViewModel.kt:163-174,190-191`; `Dtos.kt:147-178`.

---

## `PATCH /posts/{postId}/status`

**Propósito:** archivar o republicar.

**Pantallas que lo consumen:** Admin lista (botones «Archivar» y «Republicar», y «Deshacer»).

**Roles autorizados:** admin; employee a confirmar.

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `postId` | `string` | Sí | `"504"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:**

```json
{
  "status": "archived"
}
```

**Validaciones necesarias:** `status` ∈ `published | archived` (y `draft` si se aprueba la pregunta 19). Solo transiciones válidas (sección 9); si no → `409`.

**Respuesta exitosa esperada:** `200` con `{ "data": Post }`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Cambiado | Aviso emergente «El aviso se archivó.» / «El aviso volvió al muro.» con Deshacer |
| 404 | No existe | Quitarlo de la lista |
| 409 | Transición inválida | Recargar la lista |

**Reglas de negocio:**

- Idempotente: pasar al mismo estado devuelve `200`.
- Al archivar, la UI elimina el fijado (`AppViewModel.kt:130`). [Propuesta] El backend pone `isPinned = false` al archivar y, al republicar, lo deja en `false`.
- «Deshacer» = una segunda llamada con el estado anterior (y `PATCH /pin` si estaba fijado).

**Evidencia en el frontend:** `AppViewModel.kt:126-131`; `AdminListaScreen.kt:127`; `Dtos.kt:180-181`.

---

## `PATCH /posts/{postId}/pin` — [Propuesta]

**Propósito:** fijar o desfijar como importante.

**Pantallas que lo consumen:** Admin lista («Fijar» / «No fijar» y «Deshacer»).

**Roles autorizados:** admin.

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `postId` | `string` | Sí | `"502"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:**

```json
{
  "isPinned": true
}
```

**Validaciones necesarias:** `isPinned` obligatorio y `boolean`. Solo si `status = published`; si no → `409`.

**Respuesta exitosa esperada:** `200` con `{ "data": Post }`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Cambiado | Aviso emergente «Se fijó como importante en el muro.» / «Se quitó de los fijados.» |
| 404 | No existe | Quitarlo de la lista |
| 409 | Publicación archivada | Recargar |

**Reglas de negocio:** idempotente. ¿Límite de fijadas simultáneas? (pregunta 21).

**Evidencia en el frontend:** `AppViewModel.kt:119-124`; `AdminListaScreen.kt:126`; `Modelos.kt:15`.

---

## `DELETE /posts/{postId}`

**Propósito:** eliminar definitivamente una publicación.

**Pantallas que lo consumen:** Admin lista (diálogo «¿Eliminar…?» → «Sí, eliminar»).

**Roles autorizados:** admin.

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `postId` | `string` | Sí | `"504"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna adicional.

**Respuesta exitosa esperada:** `204`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 204 | Eliminada | Quitarla de la lista |
| 403 | No es admin | Mensaje |
| 404 | Ya no existe | Quitarla de la lista (idempotencia práctica) |

**Reglas de negocio:**

- Eliminación permanente, según el diálogo: «se borra para siempre y no se puede recuperar» (`AdminListaScreen.kt:89`).
- **Conflicto:** la UI ofrece «Deshacer» 5 s después de eliminar (`AppViewModel.kt:133-137`). Ver I11 y la pregunta 22.
- Se eliminan también sus adjuntos, lecturas, reacciones y notificaciones asociadas, o las notificaciones quedan con `postId` apuntando a un `404`.

**Evidencia en el frontend:** `AdminListaScreen.kt:77-99,137-141`; `AppViewModel.kt:97-98,133-137`.

---

## `PUT /posts/{postId}/read`

**Propósito:** registrar que el usuario leyó la publicación y quitar la píldora «Nuevo».

**Pantallas que lo consumen:** Detalle (al abrir).

**Roles autorizados:** resident (employee y admin a confirmar).

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `postId` | `string` | Sí | `"502"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** la publicación existe y está publicada.

**Respuesta exitosa esperada:**

```json
{
  "data": {
    "read": true,
    "readAt": "2026-09-16T16:41:03Z"
  }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Registrada, o ya estaba | Actualizar la tarjeta en caché |
| 404 | No existe | Ignorar |

**Reglas de negocio:** idempotente. Una segunda llamada conserva el primer `readAt`. [Propuesta] Si hay una notificación asociada a esa publicación, marcarla también como leída (pregunta 18).

**Evidencia en el frontend:** `Dtos.kt:214-215`; `Modelos.kt:29`; `MuroScreen.kt:216`.

---

## `PUT /posts/{postId}/reaction` y `DELETE /posts/{postId}/reaction`

**Propósito:** poner, cambiar o quitar la reacción propia.

**Pantallas que lo consumen:** **ninguna todavía.** Solo existen los DTOs.

**Roles autorizados:** resident (otros a confirmar).

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `postId` | `string` | Sí | `"502"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend (PUT):**

```json
{
  "type": "love"
}
```

DELETE no lleva cuerpo.

**Validaciones necesarias:** `type` ∈ `like | love | happy`.

**Respuesta exitosa esperada (PUT y DELETE):**

```json
{
  "data": {
    "counts": { "like": 9, "love": 7, "happy": 3 },
    "total": 19,
    "myReaction": "love"
  }
}
```

En DELETE, `myReaction` es `null`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Aplicada | Actualizar contadores |
| 404 | Publicación no existe | Ignorar |
| 422 | `type` inválido | Ignorar |

**Reglas de negocio:** una reacción por usuario y publicación. PUT reemplaza. Ambos son idempotentes.

**Evidencia en el frontend:** `Dtos.kt:201-212`.

---

## `POST /media`

**Propósito:** subir una fotografía o un PDF antes de crear o editar la publicación.

**Pantallas que lo consumen:** Admin crear («Fotografía → ADJUNTAR»). Hoy no tiene acción.

**Roles autorizados:** admin (employee a confirmar).

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`, `Content-Type: multipart/form-data`.

**Body enviado por el frontend:** `multipart/form-data` con estas partes [Propuesta]:

| Parte | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `file` | binario | Sí | JPEG, PNG o PDF |
| `altText` | texto | No | Descripción para accesibilidad |

**Validaciones necesarias:** tipo MIME permitido y tamaño máximo (sección 10). `altText` de 0–250 caracteres [Propuesta].

**Respuesta exitosa esperada:**

```json
{
  "data": {
    "id": "45",
    "postId": null,
    "originalName": "jardineria.jpg",
    "mimeType": "image/jpeg",
    "sizeBytes": 348221,
    "altText": "Residentes sembrando en el patio central",
    "sortOrder": 0,
    "contentUrl": "/api/v1/media/45/content",
    "createdAt": "2026-09-15T20:05:12Z"
  }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 201 | Subido | Mostrar la miniatura y guardar el `id` en `attachmentIds` |
| 413 | Archivo demasiado grande | «La fotografía es muy pesada.» |
| 415 | Tipo no permitido | «Solo se aceptan fotos JPG/PNG o PDF.» |
| 422 | `altText` inválido | Error del campo |

**Reglas de negocio:** [Propuesta] los adjuntos que no se asocien a ninguna publicación en 24 h se eliminan.

**Evidencia en el frontend:** `AdminCrearScreen.kt:92-99`; `Dtos.kt:119,185-197`.

---

## `GET /media/{mediaId}/content`

**Propósito:** descargar el binario de un adjunto (imagen para Coil o PDF para compartir).

**Pantallas que lo consumen:** Muro (foto de la tarjeta), Detalle (foto) y, en el futuro, PDF adjuntos.

**Roles autorizados:** resident, employee, admin, según la visibilidad de la publicación.

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `mediaId` | `string` | Sí | `"45"` |

**Query parameters:** [Propuesta] `variant` (`string`, opcional, default `original`, valores `original | thumbnail`) para miniaturas.

**Headers especiales:** request con `Authorization`. La respuesta debe incluir `Content-Type` real, `Content-Length` y `Cache-Control: private, no-store` (`app/build.gradle.kts:84-85`). Para PDF: `Content-Disposition: inline; filename="..."`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** el usuario puede ver la publicación asociada.

**Respuesta exitosa esperada:** `200` con el binario.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Autorizado | Mostrar |
| 401 | Token vencido | Ir a `acceso` |
| 404 | No existe o sin acceso | Ocultar la imagen |

**Reglas de negocio:** sin URLs públicas firmadas. Las imágenes son privadas.

**Evidencia en el frontend:** `Dtos.kt:194-195`; `app/build.gradle.kts:84-87`; `AndroidManifest.xml:28-37`; `res/xml/rutas_archivos.xml`.

---

## `DELETE /media/{mediaId}`

**Propósito:** quitar un adjunto no deseado del formulario.

**Pantallas que lo consumen:** Admin crear/editar (implícito al reemplazar la foto).

**Roles autorizados:** admin (quien lo subió).

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `mediaId` | `string` | Sí | `"45"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** [Propuesta] solo si no está asociado a una publicación. Si lo está, se quita omitiéndolo de `attachmentIds` en `PUT /posts/{postId}`.

**Respuesta exitosa esperada:** `204`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 204 | Eliminado | Quitar la miniatura |
| 404 | No existe | Quitar la miniatura |
| 409 | Asociado a una publicación | Mensaje |

**Reglas de negocio:** idempotente.

**Evidencia en el frontend:** `AdminCrearScreen.kt:92-99` (un solo adjunto visible).

---

## `GET /notifications` — [Propuesta]

**Propósito:** buzón de notificaciones del usuario.

**Pantallas que lo consumen:** Notificaciones; insignia de la barra inferior.

**Roles autorizados:** resident (otros a confirmar).

**Parámetros de ruta:** ninguno.

**Query parameters:**

| Nombre | Tipo | Obligatorio | Default | Valores permitidos | Descripción |
|---|---|---:|---|---|---|
| `page` | `number` | No | `1` | ≥ 1 | |
| `limit` | `number` | No | `20` | 1–100 | |
| `read` | `boolean` | No | — | `true \| false` | Filtrar por leídas |

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** parámetros dentro del rango.

**Respuesta exitosa esperada:**

```json
{
  "data": [
    {
      "id": "9001",
      "type": "new_post",
      "title": "Administración publicó: Menú especial de domingo",
      "body": "Toque aquí para ver los horarios y las opciones de almuerzo familiar.",
      "createdAt": "2026-09-16T15:45:00Z",
      "isRead": false,
      "readAt": null,
      "postId": "507"
    },
    {
      "id": "8990",
      "type": "reminder",
      "title": "Taller de música y canto mañana a las 11:00",
      "body": "Se hace en el salón de usos múltiples, con el coro de la parroquia.",
      "createdAt": "2026-09-15T14:00:00Z",
      "isRead": true,
      "readAt": "2026-09-15T18:12:00Z",
      "postId": "504"
    }
  ],
  "meta": { "page": 1, "limit": 20, "total": 2, "unreadCount": 1 }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Siempre, aunque `data` venga vacío | Lista o «No tiene notificaciones» (`AvisosScreen.kt:45`) |
| 401 | Sesión vencida | Ir a `acceso` |

**Reglas de negocio:**

- Excluye las archivadas. Orden `createdAt` descendente.
- `meta.unreadCount` [Propuesta] alimenta la insignia y el texto «N notificaciones sin leer». Requiere tolerar campos extra en `MetaDto` (I13).

**Evidencia en el frontend:** `AvisosScreen.kt:40-111`; `AppViewModel.kt:75-76`; `Navegacion.kt:48`; `BarraInferior.kt:60-71`.

---

## `GET /notifications/unread-count` — [Propuesta]

**Propósito:** refrescar la insignia sin descargar la lista.

**Pantallas que lo consumen:** barra inferior (Muro, Hoy, Avisos, Perfil).

**Roles autorizados:** resident.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna.

**Respuesta exitosa esperada:**

```json
{
  "data": { "unreadCount": 3 }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Siempre | Actualizar la insignia (oculta si es 0) |
| 401 | Sesión vencida | Ir a `acceso` |

**Reglas de negocio:** cuenta solo las no archivadas.

**Evidencia en el frontend:** `Navegacion.kt:48`; `BarraInferior.kt:17-18,60`.

---

## `PUT /notifications/{notificationId}/read` — [Propuesta]

**Propósito:** marcar una notificación como leída al tocarla.

**Pantallas que lo consumen:** Notificaciones.

**Roles autorizados:** resident (dueño de la notificación).

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `notificationId` | `string` | Sí | `"9001"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** pertenece al usuario del token; si no → `404`.

**Respuesta exitosa esperada:** `200` con `{ "data": Notification }` y `isRead = true`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Marcada | Abrir el detalle de `postId` |
| 404 | No existe o es de otro usuario | Recargar la lista |

**Reglas de negocio:** idempotente.

**Evidencia en el frontend:** `AvisosScreen.kt:70`; `AppViewModel.kt:78-81`.

---

## `POST /notifications/read-all` — [Propuesta]

**Propósito:** «Marcar todas como leídas».

**Pantallas que lo consumen:** Notificaciones.

**Roles autorizados:** resident.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna.

**Respuesta exitosa esperada:**

```json
{
  "data": { "updated": 3, "unreadCount": 0 }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Aplicado | Mostrar todas como leídas y el botón «Archivar las leídas» |

**Reglas de negocio:** idempotente (`updated: 0` si no había pendientes).

**Evidencia en el frontend:** `AvisosScreen.kt:57`; `AppViewModel.kt:82-84`.

---

## `POST /notifications/archive-read` — [Propuesta]

**Propósito:** «Archivar las leídas» (desaparecen del buzón).

**Pantallas que lo consumen:** Notificaciones (visible solo si `unreadCount = 0`).

**Roles autorizados:** resident.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna.

**Respuesta exitosa esperada:**

```json
{
  "data": { "archived": 4 }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Aplicado | Estado vacío «No tiene notificaciones» |

**Reglas de negocio:** solo archiva `isRead = true`. No hay UI para ver las archivadas: ¿se pueden borrar? (pregunta 23).

**Evidencia en el frontend:** `AvisosScreen.kt:103-108`; `AppViewModel.kt:85`.

---

## `GET /users` — [DTO], sin UI

**Propósito:** listar usuarios para gestión y aprobación.

**Pantallas que lo consumen:** ninguna todavía.

**Roles autorizados:** admin.

**Parámetros de ruta:** ninguno.

**Query parameters:**

| Nombre | Tipo | Obligatorio | Default | Valores permitidos | Descripción |
|---|---|---:|---|---|---|
| `page` | `number` | No | `1` | ≥ 1 | |
| `limit` | `number` | No | `20` | 1–100 | |
| `role` | `string` | No | — | `resident \| employee \| admin` | |
| `status` | `string` | No | — | `pending \| active \| disabled` | Cola de aprobación |
| `q` | `string` | No | — | 1–100 caracteres | [Propuesta] Busca en `fullName` y `email` |

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** parámetros dentro del rango.

**Respuesta exitosa esperada:** `{ "data": [User], "meta": { "page", "limit", "total" } }`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | OK | Lista |
| 403 | No es admin | Volver |

**Reglas de negocio:** nunca devuelve `secret` ni hashes.

**Evidencia en el frontend:** `Dtos.kt:15-43,217-261`.

---

## `GET /users/{userId}` — [DTO], sin UI

**Propósito:** detalle de un usuario para editarlo.

**Pantallas que lo consumen:** ninguna todavía.

**Roles autorizados:** admin.

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `userId` | `string` | Sí | `"1024"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna.

**Respuesta exitosa esperada:** `{ "data": User }`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | OK | Formulario |
| 404 | No existe | Volver |

**Reglas de negocio:** —

**Evidencia en el frontend:** `Dtos.kt:15-30`.

---

## `POST /users` — [DTO], sin UI

**Propósito:** alta de residente o de personal por un admin.

**Pantallas que lo consumen:** ninguna todavía.

**Roles autorizados:** admin.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend** (dos variantes, `Dtos.kt:225-244`).

Personal:

```json
{
  "fullName": "Gabriela Popoca Juárez",
  "email": "gabriela.popoca@residenciaguadalupe.org",
  "role": "employee",
  "authMethod": "password",
  "secret": "Temporal2026"
}
```

Residente:

```json
{
  "fullName": "Carmen Salgado Ortiz",
  "email": "carmen.salgado@correo.com",
  "authMethod": "pin",
  "secret": "4821",
  "residentProfile": {
    "phone": "7773104455",
    "department": "118",
    "admissionDate": "2026-09-01"
  },
  "role": "resident"
}
```

**Validaciones necesarias:**

- Personal: `role` ∈ `employee | admin`. No puede llevar `residentProfile` → `422` (`Dtos.kt:221-222`).
- Residente: `role = resident`. `residentProfile` es obligatorio y no nulo, con `department` obligatorio y `admissionDate` como `YYYY-MM-DD` válida no futura [Propuesta]. `phone` es opcional o `null`.
- `email` único → `409`.

**Respuesta exitosa esperada:** `201` con `{ "data": User }` y `status = "active"` [Propuesta]. `staffProfile` se crea automáticamente para el personal (`Dtos.kt:225`).

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 201 | Creado | Volver a la lista |
| 409 | Correo existente | Error en `email` |
| 422 | Validación | Errores por campo |

**Reglas de negocio:** `approvedBy` = admin del token y `approvedAt` = ahora [Propuesta].

**Evidencia en el frontend:** `Dtos.kt:225-244`.

---

## `PUT /users/{userId}` — [DTO], sin UI

**Propósito:** editar nombre, rol y estado, incluida la aprobación (`pending → active`) y la baja (`disabled`). Opcionalmente reemplaza el perfil de residente.

**Pantallas que lo consumen:** ninguna todavía.

**Roles autorizados:** admin.

**Parámetros de ruta:**

| Nombre | Tipo | Obligatorio | Ejemplo |
|---|---|---:|---|
| `userId` | `string` | Sí | `"1024"` |

**Query parameters:** ninguno.

**Headers especiales:** `Authorization`.

**Body enviado por el frontend.** Sin perfil (se conserva el existente):

```json
{
  "fullName": "Sebastián Ruiz Mendoza",
  "role": "resident",
  "status": "active"
}
```

Con perfil (reemplaza los tres campos):

```json
{
  "fullName": "Sebastián Ruiz Mendoza",
  "role": "resident",
  "status": "active",
  "residentProfile": {
    "phone": null,
    "department": "112",
    "admissionDate": "2021-03-01"
  }
}
```

**Validaciones necesarias:** `fullName`, `role` y `status` obligatorios (`Dtos.kt:246`). `residentProfile` solo si `role = resident`, nunca `null` explícito. Un admin no puede deshabilitarse ni degradarse a sí mismo [Propuesta].

**Respuesta exitosa esperada:** `200` con `{ "data": User }`.

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | Guardado | Volver |
| 404 | No existe | Volver |
| 409 | Auto-deshabilitación o último admin | Mensaje |
| 422 | Validación | Errores por campo |

**Reglas de negocio:** al pasar a `disabled`, revocar sus tokens [Propuesta]. Al aprobar, registrar `approvedBy` y `approvedAt`.

**Evidencia en el frontend:** `Dtos.kt:246-261`.

---

## `GET /audit-logs` — [DTO], sin UI

**Propósito:** consultar la bitácora.

**Pantallas que lo consumen:** ninguna todavía.

**Roles autorizados:** admin.

**Parámetros de ruta:** ninguno.

**Query parameters:**

| Nombre | Tipo | Obligatorio | Default | Valores permitidos | Descripción |
|---|---|---:|---|---|---|
| `page` | `number` | No | `1` | ≥ 1 | |
| `limit` | `number` | No | `20` | 1–100 | |
| `entityType` | `string` | No | — | Sin definir | |
| `entityId` | `string` | No | — | ID | |
| `actorId` | `string` | No | — | ID | |

**Headers especiales:** `Authorization`.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna.

**Respuesta exitosa esperada:**

```json
{
  "data": [
    {
      "id": "77120",
      "actorId": "3",
      "action": "post.archived",
      "entityType": "post",
      "entityId": "504",
      "requestId": "req_01J8ZM0A1B",
      "createdAt": "2026-09-16T17:02:44Z"
    }
  ],
  "meta": { "page": 1, "limit": 20, "total": 1 }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | OK | Lista |
| 403 | No es admin | Volver |

**Reglas de negocio:** solo lectura. Los valores de `action` y `entityType` están pendientes (pregunta 15).

**Evidencia en el frontend:** `Dtos.kt:266-275`.

---

## `GET /health`

**Propósito:** comprobar que la API responde. Se infiere de `EstadoDto { status }`.

**Pantallas que lo consumen:** ninguna.

**Roles autorizados:** Público.

**Parámetros de ruta:** ninguno.

**Query parameters:** ninguno.

**Headers especiales:** ninguno.

**Body enviado por el frontend:** ninguno.

**Validaciones necesarias:** ninguna.

**Respuesta exitosa esperada:**

```json
{
  "data": { "status": "ok" }
}
```

**Estados HTTP esperados:**

| Estado | Cuándo ocurre | Qué hará el frontend |
|---:|---|---|
| 200 | En servicio | — |
| 503 | Mantenimiento | Mensaje «Servicio en mantenimiento» |

**Reglas de negocio:** —

**Evidencia en el frontend:** `Envolturas.kt:27-28`.

---

## Solicitud de visita familiar — sin contrato

«AGENDAR VISITA FAMILIAR» (`PerfilScreen.kt:140-147`) no tiene acción ni campos. No se documenta un endpoint hasta resolver la pregunta 11.

---

# 7. JSON completos por flujo

## 7.1 Registro de residente

- **Request:** sección 4.8.
- **Éxito `201`:** sección 4.8.
- **Error de validación `422`:** sección 4.8.
- **Error de permisos:** no aplica (público).
- **Correo duplicado `409`:**

```json
{
  "error": {
    "code": "EMAIL_TAKEN",
    "message": "Ya existe una cuenta con este correo. Si no recuerda su contraseña, recepción puede reponerla.",
    "requestId": "req_01J8ZN1C2D",
    "details": [
      { "field": "email", "message": "Ya existe una cuenta con este correo." }
    ]
  }
}
```

## 7.2 Login y sesión actual

- **Request y respuesta de login:** sección 4.8.
- **`GET /auth/me` con las propuestas aplicadas:**

```json
{
  "data": {
    "id": "1024",
    "fullName": "Sebastián Ruiz Mendoza",
    "email": "sebastian.ruiz@correo.com",
    "role": "resident",
    "status": "active",
    "authMethod": "password",
    "emailVerifiedAt": "2026-09-10T17:20:00Z",
    "approvedBy": "3",
    "approvedAt": "2026-09-11T14:00:00Z",
    "createdAt": "2026-09-10T17:15:42Z",
    "updatedAt": "2026-09-11T14:00:00Z",
    "residentProfile": {
      "phone": null,
      "department": "112",
      "admissionDate": "2021-03-01",
      "roomLocation": "Ala sur, planta baja",
      "familyContact": {
        "fullName": "Mariana Ruiz",
        "relationship": "hija",
        "phone": "7772148830"
      }
    },
    "staffProfile": null,
    "preferences": {
      "notifyNotices": true,
      "notifyActivities": true,
      "notifyPhotos": false
    }
  }
}
```

- **Error de sesión `401`:**

```json
{
  "error": {
    "code": "UNAUTHENTICATED",
    "message": "Su sesión terminó. Vuelva a entrar.",
    "requestId": "req_01J8ZN4F5G",
    "details": null
  }
}
```

## 7.3 Creación, edición y listado de usuarios

No hay pantallas. Los JSON de request están en `POST /users` y `PUT /users/{userId}`. Respuestas:

- **Éxito:** `{ "data": User }` (forma de 4.8).
- **Listado vacío:**

```json
{
  "data": [],
  "meta": { "page": 1, "limit": 20, "total": 0 }
}
```

- **Validación `422`:**

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Revise los datos marcados.",
    "requestId": "req_01J8ZP2H3J",
    "details": [
      { "field": "residentProfile.admissionDate", "message": "Use el formato AAAA-MM-DD." },
      { "field": "residentProfile", "message": "Una cuenta de personal no lleva perfil de residente." }
    ]
  }
}
```

- **Permisos `403`:**

```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Solo la administración puede gestionar cuentas.",
    "requestId": "req_01J8ZP5K6L",
    "details": null
  }
}
```

## 7.4 Creación de aviso, menú y evento

- **Requests:** `POST /posts` (sección 6).
- **Éxito `201` (aviso):**

```json
{
  "data": {
    "id": "507",
    "type": "notice",
    "category": "Aviso",
    "title": "Menú especial de domingo",
    "body": "Este domingo habrá comida familiar en la terraza a partir de la 1:00 p. m.",
    "status": "published",
    "isPinned": false,
    "isRead": false,
    "author": { "id": "3", "fullName": "Administración", "area": "Administración" },
    "menuDate": null,
    "mealType": null,
    "eventStartAt": null,
    "eventEndAt": null,
    "eventLocation": null,
    "publishedAt": "2026-09-16T15:44:58Z",
    "expiresAt": null,
    "version": 1,
    "attachments": [],
    "reactions": { "counts": { "like": 0, "love": 0, "happy": 0 }, "total": 0, "myReaction": null },
    "createdAt": "2026-09-16T15:44:58Z",
    "updatedAt": "2026-09-16T15:44:58Z"
  }
}
```

- **Validación `422`:**

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Falta el título o el texto del aviso. Los residentes necesitan las dos cosas para entenderlo.",
    "requestId": "req_01J8ZQ0M7N",
    "details": [
      { "field": "title", "message": "Escriba el título del aviso." },
      { "field": "body", "message": "Escriba el texto del aviso." }
    ]
  }
}
```

- **Campo de otro tipo `422`:**

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Un aviso no lleva fecha de menú.",
    "requestId": "req_01J8ZQ2P8Q",
    "details": [
      { "field": "menuDate", "message": "No se permite en publicaciones de tipo notice." }
    ]
  }
}
```

- **Permisos `403`:**

```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Su cuenta no puede publicar este tipo de aviso.",
    "requestId": "req_01J8ZQ4R9S",
    "details": null
  }
}
```

## 7.5 Listado y detalle de publicaciones

- **Listado:** `GET /posts` (sección 6).
- **Detalle:** `{ "data": Post }` con la misma forma que el elemento `"502"` de ese ejemplo.
- **Estado vacío** (filtro «Noticias» sin resultados):

```json
{
  "data": [],
  "meta": { "page": 1, "limit": 20, "total": 0 }
}
```

- **Detalle inexistente `404`:**

```json
{
  "error": {
    "code": "POST_NOT_FOUND",
    "message": "Este aviso ya no está disponible.",
    "requestId": "req_01J8ZR1T0U",
    "details": null
  }
}
```

- **Permisos `403`** (residente con `status=all`):

```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "No tiene permiso para ver publicaciones archivadas o borradores.",
    "requestId": "req_01J8ZR3V1W",
    "details": null
  }
}
```

## 7.6 Cambio de estado de publicación

- **Archivar:** `PATCH /posts/504/status` con `{ "status": "archived" }`. Respuesta:

```json
{
  "data": {
    "id": "504",
    "type": "event",
    "category": "Actividad",
    "title": "Misa de la Asunción",
    "body": "Misa solemne en la capilla con el padre Alfonso.",
    "status": "archived",
    "isPinned": false,
    "isRead": true,
    "author": { "id": "3", "fullName": "Administración", "area": "Administración" },
    "menuDate": null,
    "mealType": null,
    "eventStartAt": "2026-08-15T12:00:00-06:00",
    "eventEndAt": "2026-08-15T13:00:00-06:00",
    "eventLocation": "Capilla",
    "publishedAt": "2026-08-10T15:00:00Z",
    "expiresAt": null,
    "version": 3,
    "attachments": [],
    "reactions": { "counts": { "like": 12, "love": 8, "happy": 2 }, "total": 22, "myReaction": null },
    "createdAt": "2026-08-10T14:58:00Z",
    "updatedAt": "2026-09-16T17:02:44Z"
  }
}
```

- **Transición inválida `409`:**

```json
{
  "error": {
    "code": "INVALID_STATUS_TRANSITION",
    "message": "Este aviso ya no se puede fijar porque está archivado.",
    "requestId": "req_01J8ZS0X2Y",
    "details": [
      { "field": "status", "message": "Transición no permitida: archived -> pinned." }
    ]
  }
}
```

- **Permisos `403`:** igual que en 7.4.

## 7.7 Reacción y registro de lectura

- **Lectura:** `PUT /posts/502/read` → `{ "data": { "read": true, "readAt": "2026-09-16T16:41:03Z" } }`.
- **Reacción:** `PUT /posts/502/reaction` con `{ "type": "love" }` → objeto `Reactions` (sección 6).
- **Validación `422`:**

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Tipo de reacción no válido.",
    "requestId": "req_01J8ZT2Z3A",
    "details": [
      { "field": "type", "message": "Use like, love o happy." }
    ]
  }
}
```

- **Publicación no disponible `404`:** igual que en 7.5.

## 7.8 Perfil propio

- **Lectura:** `GET /auth/me` (7.2).
- **Preferencias:** `PUT /auth/me/preferences` (sección 6).
- **Validación `422`:**

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Revise sus preferencias.",
    "requestId": "req_01J8ZU4B5C",
    "details": [
      { "field": "notifyPhotos", "message": "Debe ser sí o no." }
    ]
  }
}
```

- **Empleado o admin sin `residentProfile`:** `residentProfile: null`. La pantalla debe ocultar la tarjeta «Habitación / Familiar».

## 7.9 Notificaciones

- **Listado:** `GET /notifications` (sección 6).
- **Estado vacío:**

```json
{
  "data": [],
  "meta": { "page": 1, "limit": 20, "total": 0, "unreadCount": 0 }
}
```

## 7.10 Dashboard o estadísticas

**No requerido por el frontend actual.** No existe ninguna pantalla con totales o métricas.

---

# 8. Listados, filtros y paginación

**Formato encontrado en el código** (`Envolturas.kt:18-22`). Es el que el frontend ya sabe leer y **difiere del ejemplo genérico** `{ data: { items, pagination } }`. Se recomienda respetar este:

```json
{
  "data": [],
  "meta": { "page": 1, "limit": 20, "total": 0 }
}
```

`totalPages` no se envía: el cliente lo calcula como `ceil(total / limit)`. Nombres de query recomendados: `page` y `limit`, que coinciden con `MetaDto`.

> **Ninguna pantalla implementa paginación hoy.** Los valores `limit = 20` son el default de `MetaDto` (`Envolturas.kt:22`), no un requisito de UI confirmado.

| Listado | Campos mostrados | ID estable | Búsqueda | Filtros | Orden | Paginación | Total | Conteos por estado | Filtrado en |
|---|---|---|---|---|---|---|---|---|---|
| **Muro** (`MuroScreen.kt`) | `category`, `isPinned`, `isRead`, `title`, `publishedAt`, `author.fullName` o `author.area`, `body` completo, primera imagen de `attachments` | `id` | No hay buscador | Todo / Avisos / Actividades / Eventos / Noticias / Fotografías (`MuroScreen.kt:32-33`) | Fijadas primero, luego `publishedAt` descendente | La UI no define scroll infinito. Muestra la lista y el botón «Ver publicaciones anteriores». [Propuesta] `page` + `limit=20`. | No se muestra | No | **Backend.** Hoy es local sobre el mock (`AppViewModel.kt:58-60`), pero con datos reales debe filtrarse en el servidor. |
| **Banda «Hoy en la residencia»** (`MuroScreen.kt:84-129`) | `eventStartAt` (hora), `title`, `eventLocation`; máximo 3 elementos | `id` (falta en el modelo) | No | Eventos de hoy | `eventStartAt` ascendente | No: `limit=3` | No | No | Backend (`eventFrom`/`eventTo`) |
| **Agenda** (`HoyScreen.kt`) | `eventStartAt` (en Semana, con día), `eventLocation`, `title`, `nota` | `id` (falta en el modelo) | No | Pestaña Hoy / Mañana / Esta semana → rango de fechas | `eventStartAt` ascendente | No definida | No | No | Backend por rango. «Esta semana» en el mock = pasado mañana hasta el domingo (`Modelos.kt:68`). Ver pregunta 3. |
| **Archivo** (`ArchivoScreen.kt`) | Mes, `publishedAt` o `eventStartAt` (día y hora), `category`, `title` | `id` (falta en `EntradaArchivo`) | No | Ninguno | `publishedAt` descendente, agrupado por mes en el cliente | «Cargar meses anteriores» (`ArchivoScreen.kt:59`) → [Propuesta] `publishedBefore` o `page` | No | No | Backend |
| **Notificaciones** (`AvisosScreen.kt`) | `type`, `isRead`, `createdAt` relativo, `title`, `body` | `id` | No | Ninguno (todas mezcladas) | `createdAt` descendente | No definida | No | **Sí: no leídas** (insignia y texto «N notificaciones sin leer», `AvisosScreen.kt:95-99`) | Backend |
| **Admin: avisos publicados** (`AdminListaScreen.kt`) | `category`, `status` + `isPinned`, `title`, fecha y hora | `id` | No | Ninguno: muestra todos los estados | `publishedAt` descendente; los nuevos se insertan arriba (`AppViewModel.kt:191`) | No definida | No | No se muestran | Backend (`status=all`) |

---

# 9. Estados y transiciones

## 9.1 Publicación

**Encontrado en la UI** (`EstadoAviso`): `FIJADO`, `PUBLICADO`, `ARCHIVADO`. **En el DTO:** `status` ∈ `draft | published | archived`.

[Propuesta] Modelar «fijado» como la bandera `isPinned`, válida solo en `published`:

```text
            (crear)
               |
               v
  draft ----> published  <---- Republicar ----+
    \            |  ^                          |
     \           |  |  isPinned true/false     |
      \          |  +--- Fijar / No fijar      |
       \         v                             |
        +----> archived -----------------------+

  cualquier estado --(Eliminar)--> [eliminada]
```

| Transición | Botón UI | Quién | Confirmación | Reversible | Evidencia |
|---|---|---|---|---|---|
| (nuevo) → `published` | «Publicar aviso» | admin (employee ?) | No | Sí (archivar) | `AdminCrearScreen.kt:108` |
| `published` + `isPinned=false` → `isPinned=true` | «Fijar» | admin | No; Deshacer 5 s | Sí | `AdminListaScreen.kt:126`; `AppViewModel.kt:119-124` |
| `isPinned=true` → `false` | «No fijar» | admin | No; Deshacer 5 s | Sí | ídem |
| `published` → `archived` | «Archivar» | admin | No; Deshacer 5 s | Sí | `AdminListaScreen.kt:127`; `AppViewModel.kt:126-131` |
| `archived` → `published` | «Republicar» | admin | No; Deshacer 5 s | Sí | ídem |
| cualquiera → eliminada | «Eliminar» → «Sí, eliminar» | admin | **Sí**, diálogo | **No** (pero la UI ofrece Deshacer: I11) | `AdminListaScreen.kt:77-99` |
| `draft` ↔ `published` | — | — | — | — | Solo en el DTO; sin UI (pregunta 19) |
| Edición de contenido | «Editar» → «Guardar cambios» | admin | No | No se deshace | `AdminListaScreen.kt:125` |

**Botones según el estado** (`AdminListaScreen.kt:124-128`):

| Estado | Botón 2 | Botón 3 |
|---|---|---|
| published, no fijado | «Fijar» | «Archivar» |
| published, fijado | «No fijar» | «Archivar» |
| archived | «Fijar» (visible, aunque no debería permitirse; I12) | «Republicar» |

## 9.2 Usuario

`pending → active → disabled`, y `disabled → active` [Propuesta]. Solo el admin cambia el estado mediante `PUT /users/{userId}`. Sin UI.

## 9.3 Notificación

`unread → read → archived`. Lo ejecuta el dueño, sin confirmación. `read → unread` no existe en la UI. `archived` no es reversible en la UI.

## 9.4 Lectura de publicación

`no leída → leída`. Irreversible e idempotente.

## 9.5 Reacción

`null ↔ like | love | happy`. Libre para el dueño.

---

# 10. Archivos e imágenes

| Aspecto | Encontrado | Requerido / [Propuesta] |
|---|---|---|
| Dónde se muestran | Foto opcional en la tarjeta del muro (210 dp de alto, recorte `Crop`, `MuroScreen.kt:223-227`) y en el detalle (230 dp, con texto alternativo, `DetalleScreen.kt:75-76`) | Primera imagen de `attachments` por `sortOrder` |
| Dónde se seleccionan | Botón «ADJUNTAR» en el formulario de admin (`AdminCrearScreen.kt:92-99`), sin acción | `POST /media` multipart |
| Cantidad | La UI muestra **una** foto por publicación | `attachmentIds: array<string>` admite varias. [Propuesta] Máx. 1 imagen hasta que la UI tenga galería (pregunta 24). |
| Tipos | Imágenes; PDF previsto por el `FileProvider` «adjuntos» (`AndroidManifest.xml:28`, `rutas_archivos.xml`) y el comentario de Coil (`app/build.gradle.kts:84`) | [Propuesta] `image/jpeg`, `image/png`, `application/pdf` |
| Tamaño máximo | No definido | [Propuesta] 5 MB por imagen y 10 MB por PDF (pregunta 24) |
| Carga | No implementada | Subir primero (`POST /media`) y asociar con `attachmentIds` al crear o editar |
| Descarga | `contentUrl` relativa (`/api/v1/media/45/content`) con el OkHttp autenticado. Sin caché en disco: `Cache-Control: private, no-store`. | `GET /media/{mediaId}/content` con `Authorization` |
| Miniaturas | No hay | [Propuesta] `?variant=thumbnail` (~800 px de ancho) para tarjetas. Opcional. |
| Texto alternativo | Sí, en el detalle | `altText` |
| Eliminación | No hay UI | Quitar de `attachmentIds` en `PUT`, o `DELETE /media/{mediaId}` si no está asociado |
| Avatares | **No requeridos.** Perfil y detalle usan iniciales («SR») o el logotipo fijo. | — |
| Filtro «Fotografías» | Existe en el muro (`MuroScreen.kt:33`) | `hasImage=true` o una categoría o tipo nuevo (I2) |
| Preferencia «Fotografías de los eventos» | Existe en el perfil | Implica publicaciones o notificaciones de fotos (I2) |

---

# 11. Errores esperados

**Formato encontrado** (`Envolturas.kt:30-49`). El frontend todavía no procesa errores (no hay llamadas), pero los DTOs ya esperan esto:

- `error.code`: `string` estable en MAYÚSCULAS_CON_GUION_BAJO. Obligatorio.
- `error.message`: `string` en español, **mostrable al residente tal cual** (tono de la UI: «Revise su correo…»). Obligatorio.
- `error.requestId`: `string` o `null`. También en el header `X-Request-Id` [Propuesta].
- `error.details`: `array` o `null`. Cada elemento es `{ field: string, message: string }`. `field` puede ser `""` cuando el error es del cuerpo completo (`Envolturas.kt:41-44`). Los campos anidados usan punto: `residentProfile.admissionDate`.

**Error general:**

```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Algo falló de nuestro lado. Inténtelo de nuevo en unos minutos.",
    "requestId": "req_01J8ZV6D7E",
    "details": null
  }
}
```

**Validación por campos:**

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Revise los datos marcados.",
    "requestId": "req_01J8ZV8F9G",
    "details": [
      { "field": "title", "message": "Escriba el título del aviso." },
      { "field": "eventEndAt", "message": "La hora de fin debe ser posterior a la de inicio." },
      { "field": "", "message": "El cuerpo de la petición no es JSON válido." }
    ]
  }
}
```

| Estado | Códigos estables [Propuesta] | Cuándo | Comportamiento del frontend |
|---:|---|---|---|
| 400 | `BAD_REQUEST`, `MALFORMED_JSON` | JSON inválido o header faltante | Mensaje genérico en `Consejo` o en el aviso emergente |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS` | Sin token, token vencido, login fallido | Login: `Consejo`. Otros: borrar la sesión e ir a `acceso`. |
| 403 | `FORBIDDEN`, `ACCOUNT_PENDING`, `ACCOUNT_DISABLED` | Rol sin permiso, cuenta no activa | Mostrar `message`. Si la cuenta no está activa, cerrar la sesión. |
| 404 | `NOT_FOUND`, `POST_NOT_FOUND`, `NOTIFICATION_NOT_FOUND` | Recurso inexistente u oculto | Volver a la lista y mostrar el mensaje |
| 409 | `EMAIL_TAKEN`, `VERSION_CONFLICT`, `INVALID_STATUS_TRANSITION`, `IDEMPOTENCY_CONFLICT` | Duplicado, concurrencia, transición inválida | Mensaje y recarga del recurso |
| 413 | `FILE_TOO_LARGE` | Adjunto grande | Mensaje junto a «Adjuntar» |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Tipo de archivo no permitido | Mensaje junto a «Adjuntar» |
| 422 | `VALIDATION_FAILED` | Validación de campos | `details` por campo; `message` en `Consejo` |
| 429 | `RATE_LIMITED` | Demasiadas peticiones | Mensaje. Respetar `Retry-After` (segundos). |
| 500 | `INTERNAL_ERROR` | Error no controlado | Mensaje genérico con opción de reintentar |
| 503 | `SERVICE_UNAVAILABLE` | Mantenimiento | Mensaje «Servicio en mantenimiento». Respetar `Retry-After`. |

---

# 12. Datos agregados y prevención de N+1

| Pantalla | Riesgo sin agregación | Campos que deben venir juntos |
|---|---|---|
| Muro | Una petición por tarjeta para saber si es «Nuevo», su reacción, su autor o su foto | En cada elemento de `GET /posts`: `isRead` (relativo al usuario), `isPinned`, `author { id, fullName, area }`, `attachments[]` completos con `contentUrl` y `altText`, y `reactions { counts, total, myReaction }` |
| Banda «Hoy» + Muro | Dos listas en la misma pantalla | Dos peticiones independientes y paralelas (`GET /posts?type=event&eventFrom&eventTo&limit=3` y `GET /posts`). Aceptable. |
| Detalle | Autor, adjuntos y lectura por separado | `GET /posts/{postId}` con `author`, `attachments`, `isRead` y `reactions` embebidos |
| Barra inferior (4 pantallas) | Descargar el buzón completo para contar | `GET /notifications/unread-count` o `meta.unreadCount` |
| Notificaciones → Detalle | Buscar la publicación relacionada | `postId` en cada notificación |
| Perfil | Pedir perfil, contacto familiar y preferencias por separado | `GET /auth/me` con `residentProfile` (incluido `familyContact`) y `preferences` |
| Admin lista | Un conteo o estado por fila | `status` e `isPinned` en cada elemento |
| Archivo | Una petición por mes | Una sola lista paginada por `publishedAt`; el cliente agrupa por mes |

---

# 13. Inconsistencias encontradas

| # | Inconsistencia | Evidencia |
|---|---|---|
| I1 | **Categorías incompatibles.** UI: `AVISO, ACTIVIDAD, EVENTO, NOTICIA` (`Categoria`). DTO `type`: `menu, notice, event`. DTO `category`: `Actividad, Salud, Capilla, Comida`. «Noticia» no tiene equivalente. «Evento» y «Actividad» son categorías en la UI, pero «event» es un tipo y «Actividad» una categoría en el DTO. No existen «menú», «Salud» ni «Capilla» en la UI. | `data/Modelos.kt:6-11`; `data/red/Dtos.kt:85-86` |
| I2 | **«Fotografías» como filtro y preferencia**, sin categoría, tipo ni campo que lo respalde. | `MuroScreen.kt:33`; `PerfilScreen.kt:121`; `AppViewModel.kt:26` |
| I3 | **«Fijado» es un estado en la UI**, pero no existe en el contrato. | `Modelos.kt:14-18`; `Dtos.kt:89` |
| I4 | **Fechas preformateadas en los modelos** (`meta`, `fecha`, `hora`, `cuando`) frente a ISO 8601 en el DTO. La UI parsea `fecha` con `split(" · ")` para editar (`AppViewModel.kt:164-168`) y el formulario escribe `"Hoy · 10:00 a. m."` por defecto (`AppViewModel.kt:187`). | `Modelos.kt:25,34,46,56,62` |
| I5 | **`authMethod` = `password \| pin` sin selector en la UI.** El campo dice «Contraseña», pero el placeholder «••••••» (6 caracteres) sugiere PIN. | `AccesoScreen.kt:107`; `Dtos.kt:22` |
| I6 | **Registro con `status = pending`** frente a una UI que dice «Ya puede entrar al mural» y un botón «Entrar al mural» que entra sin login. | `AccesoScreen.kt:81-86`; `Dtos.kt:21` |
| I7 | **El detalle no recibe ID** y su contenido es distinto del mock del muro: el cuerpo de «Tarde de jardinería» es más largo que en `DatosEjemplo.kt:27`. | `Navegacion.kt:82`; `DetalleScreen.kt:67-73` |
| I8 | **`Actividad`, `EntradaArchivo` y `Notificacion` no tienen un ID navegable** (`postId`), aunque sus filas abren el detalle o deberían hacerlo. | `Modelos.kt:33-38,41-48,62`; `MuroScreen.kt:102`; `AvisosScreen.kt:70` |
| I9 | **Nombre del usuario en tres formas**: «Sebastián» (muro), «Sebastián Ruiz» e iniciales «SR» (perfil), «Sebastián Ruiz Mendoza» (placeholder de registro). Solo existe `fullName`. | `MuroScreen.kt:50`; `PerfilScreen.kt:42,45`; `AccesoScreen.kt:102` |
| I10 | **Habitación frente a `department`.** El perfil muestra «Habitación 112 · Ala sur, planta baja»; el DTO solo tiene `department`. El contacto familiar no existe en el DTO. | `PerfilScreen.kt:46,95-97`; `Dtos.kt:33-37` |
| I11 | **«Deshacer» después de «Eliminar para siempre».** El diálogo dice que no se puede recuperar, pero luego aparece el aviso emergente con «Deshacer» durante 5 s. | `AdminListaScreen.kt:89`; `AppViewModel.kt:133-137` |
| I12 | **Se puede fijar un aviso archivado.** El botón «Fijar» aparece en archivados y produce `FIJADO`, lo que lo republica implícitamente. | `AdminListaScreen.kt:126`; `AppViewModel.kt:123` |
| I13 | **Campos adicionales en respuestas.** kotlinx-serialization falla con claves desconocidas salvo `ignoreUnknownKeys = true`, y todavía no existe configuración de `Json`. Si el backend agrega `isPinned`, `isRead`, `unreadCount` o `area` sin que el cliente los declare, el parseo fallará. | `Envolturas.kt`, `Dtos.kt` (sin configuración de `Json` en el proyecto) |
| I14 | **`version` en `PostDto` sin uso en las ediciones.** Ningún `Editar*Request` lo envía, así que no hay concurrencia optimista. | `Dtos.kt:98,148-178` |
| I15 | **Formulario único frente a tres tipos.** El formulario de admin no permite elegir `type` ni capturar `eventLocation`, `eventEndAt`, `menuDate`, `mealType` o `expiresAt`. «Día» y «Hora» son texto libre. | `AdminCrearScreen.kt:44-110`; `Dtos.kt:108-178` |
| I16 | **Autor «Administración» frente a una persona.** Los avisos muestran «Administración» y las actividades una persona con área («Gabriela Popoca · Terapia ocupacional»). `AutorDto` no tiene área. | `DatosEjemplo.kt:17,26,43`; `DetalleScreen.kt:96`; `Dtos.kt:106` |
| I17 | **Acciones sin implementación**: «¿Olvidó su contraseña?», «Agendar visita familiar», «Adjuntar», «Cargar meses anteriores»; las entradas del archivo no son tocables. | `AccesoScreen.kt:124`; `PerfilScreen.kt:144`; `AdminCrearScreen.kt:97`; `ArchivoScreen.kt:59` |
| I18 | **Rutas de administración sin acceso ni guarda.** `admin_lista` no tiene navegación entrante; cualquier sesión podría abrirla. «Ver en el muro» navega a `muro` y apila la pantalla sobre admin. | `Navegacion.kt:96-105` |
| I19 | **«Escuchar» en la tarjeta del muro** navega al detalle igual que «Leer aviso»; no inicia la lectura. No afecta al backend: TTS es local. | `MuroScreen.kt:231-232` |
| I20 | **Formato de paginación** del código (`meta.page/limit/total`) frente al ejemplo genérico (`pagination.pageSize/totalItems/totalPages`). Se documenta el del código. | `Envolturas.kt:18-22` |
| I21 | **Rangos de agenda fijos** («Hoy · Miércoles 16») en el enum, no calculados. | `Modelos.kt:65-69` |
| I22 | **Tipo de notificación en español** («Nuevo aviso», «Recordatorio») como `String` libre, en lugar de un enum. | `Modelos.kt:43` |
| I23 | **La URL de release es un marcador** (`https://DOMINIO_REAL_API/api/v1/`). Debug usa HTTP sin TLS. | `app/build.gradle.kts:22-35`; `configuracion_red.xml` |

---

# 14. Preguntas pendientes para producto o backend

1. **Pantalla inicial por rol.** ¿Qué ve un `admin` o un `employee` al entrar: el muro con acceso a Administración, o directamente `admin_lista`? *Afecta:* navegación y Perfil. *Opciones:* (a) muro + entrada a admin en Perfil; (b) admin_lista. *Contrato:* ninguno; solo usa `user.role`.
2. **Duración de sesión y renovación.** ¿Cuánto dura el token? ¿Habrá refresh token? *Afecta:* arranque y todas las pantallas. *Opciones:* (a) token largo (30 días) sin refresh; (b) token corto + `POST /auth/refresh`. *Contrato:* respuesta de login y un endpoint adicional.
3. **Zona horaria y «esta semana».** ¿«Hoy» se calcula en `America/Mexico_City`? ¿«Esta semana» es de pasado mañana al domingo, como en el mock, o los 7 días siguientes? ¿El backend devuelve fechas en UTC (`Z`) o con desfase local? *Afecta:* Agenda y banda Hoy. *Contrato:* `eventFrom`/`eventTo` y formato de `*At`.
4. **¿La agenda son eventos?** ¿«Actividades y talleres» = `Post` con `type=event`, o una entidad aparte (sin cuerpo largo, recurrente)? *Afecta:* Hoy, banda del muro. *Opciones:* (a) reusar `GET /posts?type=event`; (b) `GET /activities`. *Contrato:* endpoint y campos.
5. **«Nota» de la agenda.** ¿Es el `body` completo, sus primeras líneas o un campo `summary` independiente? *Afecta:* Hoy. *Contrato:* nuevo campo `summary` en `Post`.
6. **Autor visible.** ¿Los avisos se firman como «Administración» (área) o con el nombre de la persona? ¿Se agrega `author.area`? *Afecta:* Muro, Detalle, confirmación de admin. *Contrato:* `AutorDto`.
7. **Permisos de `employee`.** ¿Qué tipos puede crear: solo `menu` (cocina), también `event`? ¿Puede editar o archivar lo de otros? ¿Accede a `admin_lista`? *Afecta:* matriz de permisos y Admin. *Contrato:* validación de `POST/PUT /posts` y `status=all`.
8. **Archivo para residentes.** ¿«Publicaciones anteriores» muestra solo publicaciones `published` antiguas, o también `archived`? *Afecta:* Archivo. *Contrato:* filtros de `GET /posts`.
9. **Datos de habitación y familiar.** ¿`department` es el número de habitación? ¿Se agregan `roomLocation` y `familyContact`? ¿Parentesco como texto libre o enum? ¿Uno o varios familiares? *Afecta:* Perfil. *Contrato:* `ResidentProfile`.
10. **Tamaño de letra.** ¿Se guarda en el servidor para conservarlo entre dispositivos o solo localmente? *Afecta:* Perfil. *Contrato:* `preferences.textScale`.
11. **Visita familiar.** ¿Qué hace «Agendar visita familiar»: formulario (fecha, hora, visitantes, parentesco), llamada telefónica o enlace externo? ¿Quién la aprueba? *Afecta:* Perfil. *Contrato:* entidad y endpoints nuevos, o ninguno.
12. **Recuperación de contraseña.** ¿Solo reposición en recepción (un admin reasigna `secret` en `PUT /users/{userId}` o en un endpoint dedicado) o flujo por correo? *Afecta:* Acceso. *Contrato:* `POST /auth/password-reset` o `PUT /users/{userId}/credentials`.
13. **Aprobación de registro.** ¿El registro queda `pending` hasta que recepción asigne la habitación? ¿Puede un `pending` entrar con acceso limitado? *Afecta:* Acceso. *Contrato:* respuesta de login (`403 ACCOUNT_PENDING` o `200` limitado).
14. **Edición del nombre propio.** ¿Quién puede usar `PATCH /auth/me` (`NombreRequest`)? *Afecta:* Perfil. *Contrato:* permisos.
15. **Catálogo de auditoría.** Valores de `action` y `entityType`. *Afecta:* una futura pantalla de auditoría. *Contrato:* enums.
16. **Tabla de categorías (bloqueante).** ¿Cuál es el conjunto final de `type` y `category` y su correspondencia con los filtros de la UI (Avisos, Actividades, Eventos, Noticias, Fotografías)? *Afecta:* Muro, Admin, Archivo. *Opciones:* (a) `category` ∈ `notice, activity, event, news` y `type` solo para validación; (b) mantener `Actividad, Salud, Capilla, Comida` y rediseñar la UI; (c) filtro por `type` + filtro por `category`. *Contrato:* enums de `Post` y query de `GET /posts`.
17. **Vencimiento.** ¿Una publicación con `expiresAt` pasado desaparece del muro y pasa al archivo, o se archiva automáticamente (`status=archived`)? *Afecta:* Muro, Archivo, Admin. *Contrato:* reglas de `GET /posts`.
18. **Momento de lectura.** ¿Abrir el detalle marca la lectura automáticamente en el servidor? ¿Leer la notificación marca la publicación y viceversa? *Afecta:* Muro, Detalle, Notificaciones. *Contrato:* efectos de `GET /posts/{postId}` y de los endpoints `read`.
19. **Borradores.** ¿Se necesita `draft`? La UI publica directamente. *Afecta:* Admin. *Contrato:* `status` en `POST /posts`.
20. **Notificar al editar.** ¿Editar un aviso vuelve a notificar? ¿Y republicarlo? *Afecta:* Notificaciones. *Contrato:* reglas de negocio.
21. **Límite de fijadas.** ¿Cuántas publicaciones pueden estar fijadas a la vez? *Contrato:* `409` en `PATCH /pin`.
22. **Deshacer tras eliminar.** ¿Se elimina la opción «Deshacer», el cliente retrasa el `DELETE` 5 s, o el backend implementa borrado lógico con restauración? *Afecta:* Admin. *Contrato:* posible `POST /posts/{postId}/restore`.
23. **Notificaciones archivadas.** ¿Se conservan (historial) o se borran? *Contrato:* `POST /notifications/archive-read` frente a `DELETE`.
24. **Adjuntos.** Cantidad máxima de imágenes por publicación, tamaño máximo, tipos permitidos. ¿Se aceptan PDF desde la app de admin? *Afecta:* Admin, Muro, Detalle. *Contrato:* `POST /media`.
25. **Notificaciones push.** ¿Se necesitan push (FCM) además del buzón? No hay dependencia de Firebase. *Contrato:* registro de token de dispositivo.
26. **Dominio de producción.** ¿Cuál será la URL HTTPS de release? *Afecta:* compilación de release.

---

# 15. Priorización

| Prioridad | Requerimiento | Endpoints o decisiones |
|---|---|---|
| **P0** | Login con token para Android | `POST /auth/login` |
| **P0** | Restaurar sesión, datos del usuario y rol | `GET /auth/me` |
| **P0** | Cerrar sesión | `POST /auth/logout` |
| **P0** | Registro | `POST /auth/register` |
| **P0** | Muro: listado de publicadas | `GET /posts` |
| **P0** | Detalle | `GET /posts/{postId}` |
| **P0** | Imágenes privadas | `GET /media/{mediaId}/content` |
| **P0** | Decisiones bloqueantes | Preguntas 16 (categorías), 3 (zona horaria), 1 (pantalla por rol), 13 (aprobación) y 26 (dominio) |
| **P1** | Agenda por rango | `GET /posts?type=event&eventFrom&eventTo` (pregunta 4) |
| **P1** | Crear, editar, archivar, republicar, fijar y eliminar avisos | `POST /posts`, `PUT /posts/{postId}`, `PATCH /posts/{postId}/status`, `PATCH /posts/{postId}/pin`, `DELETE /posts/{postId}` |
| **P1** | Adjuntar fotografía | `POST /media`, `DELETE /media/{mediaId}` |
| **P1** | Notificaciones e insignia | `GET /notifications`, `GET /notifications/unread-count`, `PUT /notifications/{notificationId}/read`, `POST /notifications/read-all`, `POST /notifications/archive-read` |
| **P1** | Lectura de publicación («Nuevo») | `PUT /posts/{postId}/read` |
| **P1** | Perfil del residente completo | `residentProfile` ampliado en `GET /auth/me` (pregunta 9) |
| **P1** | Archivo paginado | `GET /posts?publishedBefore&page` |
| **P2** | Preferencias de notificación | `GET/PUT /auth/me/preferences` |
| **P2** | Reacciones | `PUT/DELETE /posts/{postId}/reaction` |
| **P2** | Cambio de credencial y nombre | `PUT /auth/me/credentials`, `PATCH /auth/me` |
| **P2** | Gestión de usuarios | `GET /users`, `GET /users/{userId}`, `POST /users`, `PUT /users/{userId}` |
| **P2** | Auditoría | `GET /audit-logs` |
| **P2** | Salud del servicio | `GET /health` |
| **P2** | Visita familiar y recuperación de contraseña | Preguntas 11 y 12 |

---

# 16. Checklist final para backend

- [x] Entidades y campos definidos (sección 3). Pendientes: visita familiar y la agenda como entidad separada.
- [x] Tipos, nullabilidad y enums definidos. **Bloqueado:** enum de `category`/`type` (pregunta 16).
- [x] Endpoints de lectura definidos.
- [x] Endpoints de escritura definidos.
- [x] Requests JSON completos.
- [x] Responses JSON completas.
- [ ] Roles y permisos definidos. **Pendiente:** permisos de `employee` (pregunta 7); toda la matriz es propuesta.
- [x] Validaciones definidas. Las longitudes máximas son propuestas.
- [x] Errores definidos.
- [x] Filtros y paginación definidos. La paginación es propuesta: la UI no la implementa.
- [x] Estados y transiciones definidos. **Pendiente:** Deshacer al eliminar (pregunta 22).
- [x] Archivos e imágenes definidos. **Pendiente:** límites (pregunta 24).
- [x] Necesidades agregadas identificadas.
- [x] Preguntas pendientes enumeradas.
- [x] Prioridades asignadas.

---

## Resumen estructurado

```json
{
  "backendRequirementsSummary": {
    "screensReviewed": 9,
    "entities": [
      "User",
      "ResidentProfile",
      "StaffProfile",
      "Session",
      "Post",
      "Media",
      "Reactions",
      "PostRead",
      "Notification",
      "Preferences",
      "AuditLog",
      "Error"
    ],
    "requiredEndpoints": [
      { "method": "POST", "path": "/auth/register", "roles": ["public"], "priority": "P0", "consumers": ["AccesoScreen"] },
      { "method": "POST", "path": "/auth/login", "roles": ["public"], "priority": "P0", "consumers": ["AccesoScreen"] },
      { "method": "GET", "path": "/auth/me", "roles": ["resident", "employee", "admin"], "priority": "P0", "consumers": ["Navegacion", "MuroScreen", "PerfilScreen"] },
      { "method": "POST", "path": "/auth/logout", "roles": ["resident", "employee", "admin"], "priority": "P0", "consumers": ["PerfilScreen"] },
      { "method": "GET", "path": "/posts", "roles": ["resident", "employee", "admin"], "priority": "P0", "consumers": ["MuroScreen", "HoyScreen", "ArchivoScreen", "AdminListaScreen"] },
      { "method": "GET", "path": "/posts/{postId}", "roles": ["resident", "employee", "admin"], "priority": "P0", "consumers": ["DetalleScreen", "AdminCrearScreen"] },
      { "method": "GET", "path": "/media/{mediaId}/content", "roles": ["resident", "employee", "admin"], "priority": "P0", "consumers": ["MuroScreen", "DetalleScreen"] },
      { "method": "POST", "path": "/posts", "roles": ["admin", "employee"], "priority": "P1", "consumers": ["AdminCrearScreen"] },
      { "method": "PUT", "path": "/posts/{postId}", "roles": ["admin", "employee"], "priority": "P1", "consumers": ["AdminCrearScreen"] },
      { "method": "PATCH", "path": "/posts/{postId}/status", "roles": ["admin"], "priority": "P1", "consumers": ["AdminListaScreen"] },
      { "method": "PATCH", "path": "/posts/{postId}/pin", "roles": ["admin"], "priority": "P1", "consumers": ["AdminListaScreen"] },
      { "method": "DELETE", "path": "/posts/{postId}", "roles": ["admin"], "priority": "P1", "consumers": ["AdminListaScreen"] },
      { "method": "PUT", "path": "/posts/{postId}/read", "roles": ["resident"], "priority": "P1", "consumers": ["DetalleScreen", "MuroScreen"] },
      { "method": "POST", "path": "/media", "roles": ["admin", "employee"], "priority": "P1", "consumers": ["AdminCrearScreen"] },
      { "method": "DELETE", "path": "/media/{mediaId}", "roles": ["admin", "employee"], "priority": "P1", "consumers": ["AdminCrearScreen"] },
      { "method": "GET", "path": "/notifications", "roles": ["resident"], "priority": "P1", "consumers": ["AvisosScreen"] },
      { "method": "GET", "path": "/notifications/unread-count", "roles": ["resident"], "priority": "P1", "consumers": ["BarraInferior"] },
      { "method": "PUT", "path": "/notifications/{notificationId}/read", "roles": ["resident"], "priority": "P1", "consumers": ["AvisosScreen"] },
      { "method": "POST", "path": "/notifications/read-all", "roles": ["resident"], "priority": "P1", "consumers": ["AvisosScreen"] },
      { "method": "POST", "path": "/notifications/archive-read", "roles": ["resident"], "priority": "P1", "consumers": ["AvisosScreen"] },
      { "method": "GET", "path": "/auth/me/preferences", "roles": ["resident"], "priority": "P2", "consumers": ["PerfilScreen"] },
      { "method": "PUT", "path": "/auth/me/preferences", "roles": ["resident"], "priority": "P2", "consumers": ["PerfilScreen"] },
      { "method": "PUT", "path": "/posts/{postId}/reaction", "roles": ["resident"], "priority": "P2", "consumers": [] },
      { "method": "DELETE", "path": "/posts/{postId}/reaction", "roles": ["resident"], "priority": "P2", "consumers": [] },
      { "method": "PUT", "path": "/auth/me/credentials", "roles": ["resident", "employee", "admin"], "priority": "P2", "consumers": [] },
      { "method": "PATCH", "path": "/auth/me", "roles": ["resident", "employee", "admin"], "priority": "P2", "consumers": [] },
      { "method": "GET", "path": "/users", "roles": ["admin"], "priority": "P2", "consumers": [] },
      { "method": "GET", "path": "/users/{userId}", "roles": ["admin"], "priority": "P2", "consumers": [] },
      { "method": "POST", "path": "/users", "roles": ["admin"], "priority": "P2", "consumers": [] },
      { "method": "PUT", "path": "/users/{userId}", "roles": ["admin"], "priority": "P2", "consumers": [] },
      { "method": "GET", "path": "/audit-logs", "roles": ["admin"], "priority": "P2", "consumers": [] },
      { "method": "GET", "path": "/health", "roles": ["public"], "priority": "P2", "consumers": [] }
    ],
    "missingDecisions": [
      "Tabla final de type y category y su correspondencia con los filtros del muro",
      "Zona horaria de negocio y definicion de hoy, manana y esta semana",
      "Pantalla inicial y navegacion segun el rol",
      "Duracion del token y refresh",
      "Si la agenda es Post de tipo event o una entidad separada",
      "Origen del campo nota de la agenda",
      "Autor visible: area o persona",
      "Permisos del rol employee",
      "Contenido del archivo para residentes",
      "Datos de habitacion y contacto familiar",
      "Tamano de letra local o en servidor",
      "Flujo de visita familiar",
      "Recuperacion de contrasena",
      "Acceso de cuentas pending",
      "Momento en que se registra la lectura",
      "Uso de borradores",
      "Notificar al editar o republicar",
      "Limite de publicaciones fijadas",
      "Deshacer tras eliminar",
      "Conservacion de notificaciones archivadas",
      "Limites y tipos de adjuntos",
      "Notificaciones push",
      "Dominio HTTPS de produccion",
      "Catalogo de acciones de auditoria",
      "Vencimiento de publicaciones con expiresAt",
      "Permiso para editar el nombre propio"
    ],
    "blockingIssues": [
      "Categorias de la UI incompatibles con type y category del contrato",
      "El contrato no tiene campo para publicaciones fijadas",
      "El formulario de administracion usa dia y hora como texto libre y no captura campos de menu ni de evento",
      "No existe contrato de notificaciones",
      "La pantalla de detalle no recibe identificador",
      "No existe capa de red ni de sesion en la app",
      "No hay navegacion ni guardas por rol",
      "URL de release sin definir"
    ]
  }
}
```
