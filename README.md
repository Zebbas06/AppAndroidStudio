# Residencia Guadalupe News — app Android

Aplicación Android (Kotlin + Jetpack Compose) del mural de avisos de la
Residencia Guadalupe A.C. (Cuernavaca). Los residentes leen avisos, menús y
eventos, reciben notificaciones y reaccionan; el personal publica y administra
avisos; la administración además aprueba residentes y gestiona cuentas.

La app está **conectada a la API desplegada en AWS** (API Gateway → Elastic
Beanstalk Node.js 22 → RDS MySQL). Ya no usa datos de ejemplo.

> Entorno de **pruebas** con datos ficticios. El tramo Gateway → Beanstalk usa
> HTTP provisional: no es una certificación de producción ni autoriza cargar
> datos personales reales de residentes.

---

## Índice

1. [Estado actual](#1-estado-actual)
2. [Requisitos y cómo abrir el proyecto](#2-requisitos-y-cómo-abrir-el-proyecto)
3. [Compilar, instalar y probar desde terminal](#3-compilar-instalar-y-probar-desde-terminal)
4. [Configuración: URL de la API y variantes](#4-configuración-url-de-la-api-y-variantes)
5. [Estructura del proyecto](#5-estructura-del-proyecto)
6. [Arquitectura de la app](#6-arquitectura-de-la-app)
7. [Pantallas, navegación y roles](#7-pantallas-navegación-y-roles)
8. [Detalles de implementación Android](#8-detalles-de-implementación-android)
9. [Documentación de la API](#9-documentación-de-la-api)
10. [Pruebas](#10-pruebas)
11. [Verificación realizada contra AWS](#11-verificación-realizada-contra-aws)
12. [Problemas conocidos y solución de problemas](#12-problemas-conocidos-y-solución-de-problemas)
13. [Fuera de alcance y pendientes](#13-fuera-de-alcance-y-pendientes)
14. [Documentos de referencia](#14-documentos-de-referencia)

---

## 1. Estado actual

| Área | Estado |
|---|---|
| Sesión (login contraseña/PIN, registro, restaurar, logout, 401) | Conectado y verificado en AWS |
| Muro, filtros, paginación, banda «Hoy en la residencia» | Conectado y verificado |
| Agenda (Hoy / Mañana / Esta semana) | Conectado y verificado |
| Detalle, lectura, reacciones, adjuntos (imagen/PDF) | Conectado y verificado |
| Notificaciones (buzón, contador, leer, leer todas, archivar leídas) | Conectado y verificado |
| Perfil (datos, preferencias, nombre, credencial, tamaño de letra) | Conectado y verificado |
| Administración de avisos (crear, editar, fijar, archivar, eliminar) | Conectado y verificado |
| Usuarios (pendientes, aprobar, alta, editar, deshabilitar, reponer) | Conectado y verificado |
| Pruebas unitarias | 21 pruebas, todas pasan |
| Prueba de integración contra AWS | 43/43 pasos correctos (23/09/2026) |

---

## 2. Requisitos y cómo abrir el proyecto

| Herramienta | Versión |
|---|---|
| Android Studio | Reciente (incluye su propio JDK en `Contents/jbr`) |
| Gradle (wrapper) | 9.3.0 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 (Compose compiler 1.5.14) |
| compileSdk / targetSdk | 34 |
| minSdk | 24 (Android 7.0) |
| Java/Kotlin target | 17 |

Pasos:

1. Android Studio → **Open** → elija **la carpeta raíz del proyecto**
   (`ResidenciaGuadalupe/`, donde están `settings.gradle.kts` y `gradlew`).
2. Espere el *Gradle sync*. Solo descarga dependencias de Google y Maven Central.
3. **Run** en un emulador o teléfono con API 24 o superior y acceso a internet.

Las tipografías (Playfair Display y Source Sans 3) se descargan de Google
Fonts en tiempo de ejecución: el emulador necesita Google Play Services.

---

## 3. Compilar, instalar y probar desde terminal

Si `java` no está en el sistema, use el JDK de Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

| Tarea | Comando |
|---|---|
| APK de depuración | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| APK de release (sin firmar) | `./gradlew :app:assembleRelease` |
| Pruebas unitarias | `./gradlew :app:testDebugUnitTest` |
| Instalar en el emulador | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Prueba de integración contra AWS | ver [sección 10.2](#102-prueba-de-integración-contra-la-api-real) |

Notas del entorno (JDK 25 que trae Android Studio actualmente):

- Si aparece `Daemon compilation failed`, Gradle reintenta sin el daemon y
  compila igual. Para evitar el mensaje: `-Pkotlin.compiler.execution.strategy=in-process`.
- `assembleRelease` falla en `lintVitalAnalyzeRelease` con el mensaje `25.0.3`:
  el lint de AGP 8.5 no reconoce JDK 25. **No es un error del código.** Use un
  JDK 17 o 21, o compile omitiendo ese paso:
  `./gradlew :app:assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease`.

---

## 4. Configuración: URL de la API y variantes

La URL se define una sola vez en `app/build.gradle.kts`:

```kotlin
val URL_API_PRUEBAS = "https://jmuq4nqote.execute-api.us-east-1.amazonaws.com/api/v1/"
```

y llega al código como `BuildConfig.URL_API`.

| Variante | URL | Tráfico HTTP en claro | Logs de red |
|---|---|---|---|
| `debug` | Gateway de pruebas (HTTPS) | Solo a `10.0.2.2` (API local desde el emulador) | `HttpLoggingInterceptor` nivel BASIC, cabecera `Authorization` oculta |
| `release` | Gateway de pruebas (HTTPS) mientras no exista dominio de producción | Prohibido | Ninguno |

Reglas:

- **Usar siempre la dirección del API Gateway**, nunca el host de Elastic
  Beanstalk ni la base MySQL.
- La base de Retrofit lleva **barra final**, y las rutas son relativas sin `/`
  inicial y sin repetir `api/v1` (`auth/login`, `posts/{id}`).
- Para una API local: `http://10.0.2.2:8080/api/v1/` (solo en debug; ver
  `app/src/debug/res/xml/configuracion_red.xml`).
- No desactivar la validación de certificados.

---

## 5. Estructura del proyecto

```
ResidenciaGuadalupe/
├── README.md                     ← este documento
├── BACKEND_REQUIREMENTS.md       ← requisitos que el frontend pidió al backend (histórico)
├── documentacionAPI/             ← documentación entregada por el backend (ver §14)
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── app/
    ├── build.gradle.kts          ← SDKs, URL_API, dependencias, desugaring
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/residenciaguadalupe/news/
        │   │   ├── AplicacionResidencia.kt   ← crea cliente HTTP, repositorio y Coil (una vez)
        │   │   ├── MainActivity.kt           ← única Activity; aplica la escala de letra
        │   │   ├── Navegacion.kt             ← rutas, barra inferior por rol, arranque
        │   │   ├── AppViewModel.kt           ← sesión, acceso, muro, agenda, detalle, buzón, perfil, archivo
        │   │   ├── AdminViewModel.kt         ← lista y formulario de publicaciones
        │   │   ├── UsuariosViewModel.kt      ← gestión de usuarios (admin)
        │   │   ├── data/
        │   │   │   ├── Modelos.kt            ← modelos de UI y conversión desde DTOs
        │   │   │   ├── Fechas.kt             ← zona America/Mexico_City y textos de fecha
        │   │   │   ├── red/
        │   │   │   │   ├── ApiServicio.kt    ← interfaz Retrofit (todas las operaciones)
        │   │   │   │   ├── ClienteApi.kt     ← Json, OkHttp con Bearer, Retrofit, URL de adjuntos
        │   │   │   │   ├── Dtos.kt           ← DTOs del contrato OpenAPI
        │   │   │   │   ├── Envolturas.kt     ← {data}, {data,meta}, {error}
        │   │   │   │   └── ErrorApi.kt       ← traducción de errores y llamar()/llamarSinCuerpo()
        │   │   │   ├── repositorio/Repositorio.kt ← único punto de acceso a la API
        │   │   │   └── sesion/AlmacenSesion.kt    ← token, vencimiento y usuario persistidos
        │   │   └── ui/
        │   │       ├── theme/ (Color.kt, Type.kt)   ← paleta y tipografía del mockup
        │   │       ├── componentes/                  ← Comunes.kt, BarraInferior.kt, AvisoEmergente.kt
        │   │       └── pantallas/                    ← 11 pantallas (ver §7)
        │   └── res/
        │       ├── drawable/ (logo.png, foto_capilla.jpg, foto_comedor.jpg, foto_jardineria.jpg)
        │       ├── values/ (strings, colors, themes, font_certs)
        │       └── xml/ (reglas_respaldo, reglas_extraccion, rutas_archivos)
        ├── debug/     ← Depuracion.kt (logs) y configuracion_red.xml
        ├── release/   ← Depuracion.kt sin logs
        └── test/      ← pruebas unitarias y de integración (ver §10)
```

---

## 6. Arquitectura de la app

```
Pantallas Compose (ui/pantallas)
        │  leen estado observable (mutableStateOf) y llaman métodos
        ▼
ViewModels: AppViewModel · AdminViewModel · UsuariosViewModel   (AndroidViewModel, alcance Activity)
        │  capturan ErrorApi y lo convierten en estado de pantalla
        ▼
Repositorio (data/repositorio)  ── DTO → modelo, validación de adjuntos, señal "cambios"
        │
        ▼
ClienteApi: OkHttp (Bearer + cierre ante 401) → Retrofit + kotlinx.serialization → ApiServicio
        │                                      └── Coil reutiliza el mismo OkHttp
        ▼
API Gateway (HTTPS) → Elastic Beanstalk → RDS MySQL
```

- **Contenedor manual.** `AplicacionResidencia` crea de forma perezosa
  `AlmacenSesionPreferencias`, `ClienteApi`, `Repositorio` y el `ImageLoader`
  de Coil. Los ViewModels los obtienen con `app as AplicacionResidencia`.
- **Estado.** Cada ViewModel expone propiedades `mutableStateOf` /
  `mutableStateListOf` con `private set`. Las pantallas no guardan datos de
  red, solo estado visual (campos de formulario, diálogos).
- **Errores.** Toda llamada pasa por `llamar { }` o `llamarSinCuerpo { }`
  (`ErrorApi.kt`), que devuelven el cuerpo o lanzan `ErrorApi` con un mensaje
  que siempre se puede mostrar al usuario.
- **Recarga del muro.** `Repositorio.cambios` (un `StateFlow` contador) sube en
  cada creación, edición, fijado, archivado o borrado; `AppViewModel` marca el
  muro como desactualizado y `MuroScreen` lo recarga al volver.

---

## 7. Pantallas, navegación y roles

### 7.1 Rutas (`Navegacion.kt`)

| Ruta | Pantalla | Archivo | Quién la ve |
|---|---|---|---|
| `acceso` | Login, registro, cuenta pendiente | `AccesoScreen.kt` | Sin sesión |
| `muro` | Muro de publicaciones | `MuroScreen.kt` | Todos |
| `hoy` | Agenda de actividades | `HoyScreen.kt` | Todos |
| `detalle/{postId}` | Detalle de publicación | `DetalleScreen.kt` | Todos |
| `avisos` | Buzón de notificaciones | `AvisosScreen.kt` | Solo residente |
| `perfil` | Perfil, preferencias, cuenta | `PerfilScreen.kt` | Todos |
| `archivo` | Publicaciones anteriores | `ArchivoScreen.kt` | Todos |
| `admin_lista` | Administración de avisos | `AdminListaScreen.kt` | Admin y empleado |
| `admin_crear` | Crear / editar aviso | `AdminCrearScreen.kt` | Admin y empleado |
| `usuarios` | Lista de usuarios | `UsuariosScreen.kt` | Solo admin |
| `usuario/{id}` | Alta / edición / aprobación (`id = nuevo` para alta) | `UsuariosScreen.kt` | Solo admin |

### 7.2 Flujo de arranque y sesión

1. Mientras `AppViewModel.restaurando` es `true` se muestra «Abriendo el mural…».
2. Si hay token guardado y no vencido → `GET auth/me` → entra al **muro**.
   Un 401 cierra la sesión (lo hace el interceptor); un 403 la cierra con el
   mensaje de la API; sin red entra con el usuario guardado.
3. Si el token ya venció → se borra sin llamar a la red → **acceso**.
4. Cuando la sesión se cierra por cualquier motivo (logout, 401, token
   revocado), `Navegacion` limpia la pila y vuelve a **acceso**, mostrando el
   motivo («Su sesión terminó. Vuelva a entrar.»).

### 7.3 Navegación por rol

| | Residente | Empleado | Admin |
|---|:-:|:-:|:-:|
| Pantalla inicial | Muro | Muro | Muro |
| Barra inferior | Muro · Hoy · Avisos (con insignia) · Perfil | Muro · Hoy · Perfil | Muro · Hoy · Perfil |
| «Administrar →» en el muro y tarjeta «Administración» en el perfil | — | ✓ | ✓ |
| Botón «Usuarios y residentes» | — | — | ✓ |
| Preferencias de notificación | ✓ | — | — |
| Reaccionar / registrar lectura / etiqueta «Nuevo» | ✓ | Solo ve conteos | Solo ve conteos |

La API aplica los permisos aunque la interfaz mostrara un botón indebido.

### 7.4 Qué hace cada pantalla

**Acceso.** Tres modos: login, registro y «cuenta creada».
- Selector visible **Contraseña / PIN**. Con PIN el teclado es numérico y solo
  admite dígitos (4–6).
- Validación local: correo con formato válido; en registro, contraseña de
  8–128 caracteres o PIN de 4–6 dígitos, y nombre de 2–150.
- La tecla ✓ del teclado envía el formulario.
- 401 muestra el mensaje de la API («El correo o la contraseña no
  coinciden.»). **403 `ACCOUNT_PENDING`** muestra la tarjeta «Cuenta pendiente».
- El registro exitoso (201, `resident/pending`) **no entra al muro**: indica que
  recepción debe confirmar.
- «¿Olvidó su contraseña?» explica que recepción la repone (no hay recuperación
  por correo).

**Muro.**
- Encabezado con saludo según la hora de Ciudad de México y el nombre de pila.
- Banda verde «Hoy en la residencia» con los eventos del día.
- Filtros: Todo, Avisos, Actividades, Eventos, Noticias y Fotografías
  (`hasImage=true`).
- Tarjetas con categoría, «Fijado · Importante», «Nuevo» (residente sin leer),
  fecha, extracto, foto privada o marca de PDF.
- «Cargar más publicaciones» (paginación con `meta.total`), «Ver publicaciones
  anteriores».
- Estados de carga (esqueleto), error con «Reintentar» y vacío.

**Hoy (agenda).**
- Pestañas Hoy, Mañana y Esta semana (lunes a domingo, provisional).
- Consulta `type=event` con `eventFrom` inclusivo y `eventTo` exclusivo,
  ordenado por `eventStartAt`.
- Cada fila muestra hora, lugar, título y un extracto del cuerpo; al tocarla
  abre el detalle.

**Detalle.**
- `GET posts/{id}`. Si el usuario es residente y no la había leído,
  `PUT posts/{id}/read`; luego actualiza la tarjeta del muro, la notificación y
  el contador.
- Muestra autor y área, categoría y fijado, cuerpo en párrafos, imagen privada,
  o PDF con «Abrir PDF» (se descarga con Bearer y se abre con otra app
  mediante `FileProvider`).
- Reacciones Me gusta / Me encanta / Me alegra: tocar la propia la quita.
- Ficha Cuándo / Dónde / Publica.

**Avisos (buzón, solo residente).**
- Tarjetas sin leer y filas leídas.
- Tocar una la marca (`PUT notifications/{id}/read`) y abre su publicación.
- «Marcar todas como leídas» (`read-all`), «Archivar las leídas»
  (`archive-read`) y «Ver notificaciones anteriores».

**Perfil.**
- Iniciales, nombre y resumen («Habitación 112 · Desde 2021», o rol y área si
  es personal).
- Tarjeta de Administración (personal).
- Tamaño de letra A− / A / A+ (local, no viaja a la API).
- Datos:
  - Residente: habitación y ubicación, correo, teléfono, familiar e ingreso.
  - Personal: correo, área y función.
  - Si falta un dato se muestra «Sin registrar». Si recepción aún no completa
    el perfil, se dice así en vez de inventarlo.
- Preferencias de notificación (residente): se guardan al instante y vuelven al
  valor anterior si falla.
- Cambiar nombre. Cambiar contraseña o PIN (pide la actual; cierra las demás
  sesiones).
- Cerrar sesión.

**Archivo.**
- Publicaciones con `publishedBefore = inicio de hoy` (hora de CDMX), agrupadas
  por mes.
- «Cargar meses anteriores». Cada entrada abre el detalle.

**Administración · avisos.**
- Admin ve todas las publicaciones (`status=all`); el empleado solo las suyas.
- Acciones por tarjeta: Editar, Fijar/No fijar (solo admin y solo publicadas),
  Archivar/Republicar y Eliminar (solo admin).
- Fijar y archivar muestran **Deshacer**, que envía la operación inversa;
  deshacer el archivado de un aviso fijado lo republica **y lo vuelve a fijar**.
- Eliminar pide confirmación y **no tiene Deshacer**: la API no permite
  recuperarlo.

**Administración · crear / editar.**
- Tipo: Aviso (`notice`), Menú (`menu`) o Evento (`event`); no se puede
  cambiar al editar.
- Título (≤150), categoría, texto (≤5000).
- Evento: día, hora de inicio y fin, lugar (≤120). Menú: día y comida
  (desayuno, comida, cena o todo el día).
- «Mostrar en el muro hasta» (opcional → `expiresAt`), Fijar (solo admin).
- Adjuntar una foto o un PDF, con vista previa privada.
- Tras publicar muestra una confirmación con la vista previa.
- Un 409 de versión ofrece «Cargar la versión actual».

**Usuarios (admin).**
- Filtros: Pendientes (por defecto), Activas, Bajas y Todas; búsqueda por nombre
  o correo; «Dar de alta a alguien».
- La ficha permite:
  - **Confirmar registro**: exige habitación y fecha de ingreso.
  - Editar nombre, tipo de cuenta y estado (Pendiente, Activa, Deshabilitada).
  - Completar el perfil del residente: habitación, ingreso, ubicación,
    teléfono y familiar de contacto. Al personal, su área.
  - **Reponer contraseña o PIN**, que revoca todas las sesiones de esa persona.

---

## 8. Detalles de implementación Android

### 8.1 Sesión (`data/sesion/AlmacenSesion.kt`)

- Se guarda `token`, `vence_en` (ISO) y el `usuario` (JSON) en
  SharedPreferences privadas **`sesion_residencia.xml`**.
- Ese archivo está **excluido del respaldo en la nube y de la transferencia
  entre dispositivos** (`res/xml/reglas_respaldo.xml`, `reglas_extraccion.xml`).
- Expone un `StateFlow<Sesion?>`; `AppViewModel` lo observa para entrar y salir.
- No hay refresh token: al vencer (8 h) el usuario vuelve a entrar.
- Al cerrar sesión: `POST auth/logout` (si hay red) y **siempre** se borra la
  sesión local, los PDF descargados (`cache/adjuntos/`) y la caché en memoria
  de imágenes.

### 8.2 Cliente HTTP (`data/red/ClienteApi.kt`)

- Timeouts: conexión 20 s, lectura 30 s, escritura 60 s (subidas lentas).
- **Interceptor de token**:
  - Añade `Authorization: Bearer <token>` solo a peticiones hacia el host de
    la API.
  - Ante un **401** en una petición que llevaba token (salvo `auth/login`),
    borra la sesión con el motivo «Su sesión terminó».
  - Antes de borrarla comprueba que el token rechazado siga siendo el actual,
    para no cerrar un login nuevo.
- **Json** (`ClienteApi.json`):
  - `ignoreUnknownKeys = true`: la API puede añadir campos.
  - `encodeDefaults = true`: viajan `type`, `clientType: "android"` y
    `attachmentIds: []`.
  - `explicitNulls = false`: un `null` se **omite**. En este contrato omitir
    un campo nullable equivale a su valor por defecto. Además evita el 422 por
    `type` en un PUT y por `residentProfile: null`.
  - `coerceInputValues = true`.
- `urlAdjunto(contentUrl)` resuelve `/api/v1/media/45/content` contra el
  **origen**, sin duplicar `/api/v1/`.
- **Coil** usa el mismo OkHttp (las imágenes llevan el Bearer), **sin caché en
  disco** (`private, no-store`); la memoria se vacía al cerrar sesión.

### 8.3 Errores (`data/red/ErrorApi.kt`)

`ErrorApi(estado, codigo, mensaje, referencia, detalles, reintentarEn)`:

- Cuerpo de la aplicación `{error:{code,message,requestId,details}}` → se usa
  `message` tal cual, más los mensajes por campo (`mensajeCompleto`,
  `mensajeDe(campo)`).
- Cuerpo de AWS/infraestructura (p. ej. `{"message":"Request Timeout"}` o
  HTML) → mensaje propio según el status; **nunca se muestra HTML ni texto
  técnico**.
- `referencia` = `requestId` del cuerpo, o las cabeceras `X-Request-Id` /
  `apigw-requestid`. Se muestra como «Referencia para soporte» en errores
  5xx. Nunca incluye el token.
- 429 → «Espere N segundos…» con `Retry-After`.
- Sin red → `estado = 0` («No hay conexión con la residencia…»).

| Status | Mensaje genérico (cuando no viene `error.message`) |
|---|---|
| 0 (sin red) / timeout | No hay conexión… / La conexión tardó demasiado… |
| 400, 422 | Revise los datos e inténtelo de nuevo. |
| 401 | Su sesión terminó. Vuelva a entrar. |
| 403 | No tiene permiso para hacer esto. |
| 404 | Esta publicación ya no está disponible. |
| 408, 504 | La conexión tardó demasiado… |
| 409 | Alguien más hizo cambios… |
| 413 | El archivo es demasiado grande. |
| 415 | Ese tipo de archivo no se admite. Use JPG, PNG o PDF. |
| 429 | Demasiados intentos. Espere N segundos… |
| 5xx | El servidor de la residencia no responde… |

### 8.4 Modelos y categorías (`data/Modelos.kt`)

- `Categoria`: AVISO, ACTIVIDAD, EVENTO, NOTICIA. `desdeApi()` acepta mayúsculas
  y trata **Salud, Capilla y Comida como alias de Aviso**. No son filtros
  distintos.
- `TipoPublicacion` (`notice` / `menu` / `event`) es **independiente** de la
  categoría: un menú puede tener categoría Aviso.
- `Comida` (`breakfast`, `lunch`, `dinner`, `daily`), `Rol`, `EstadoCuenta`.
- `EstadoAviso` (solo para la etiqueta de administración) combina `status` con
  `isPinned`: Fijado, Publicado, Archivado o Borrador.
- `Publicacion` (con `meta`, `cuando` y `estadoAdmin` calculados), `Adjunto`,
  `Reacciones`, `Actividad`, `Notificacion`, `MesArchivo`.
- Los **IDs son `String`** siempre (BIGINT de MySQL).

### 8.5 Fechas y zona horaria (`data/Fechas.kt`)

- La zona de negocio es **America/Mexico_City** sin importar la del teléfono.
- Instantes `*At`: ISO 8601 UTC. `menuDate` y `admissionDate`: fecha civil
  `YYYY-MM-DD` (no se convierten a instante).
- Los rangos de agenda se calculan en CDMX y se envían en UTC (p. ej. hoy
  23/09 → `eventFrom=2026-09-23T06:00:00Z`, `eventTo=2026-09-24T06:00:00Z`).
- **Protección contra tzdata vieja.** México abolió el horario de verano en
  octubre de 2022. Un teléfono con datos de zona anteriores (p. ej. Android 10
  con tzdata 2019b) aplicaría CDT y correría una hora la agenda y los eventos.
  Si las reglas del dispositivo no dan UTC-6 en verano, la app usa
  **UTC-6 fijo**.
- `java.time` en minSdk 24 funciona gracias a *core library desugaring*
  (`desugar_jdk_libs 2.0.4`).
- Textos: «10:30 a. m.», «Hoy a las 8:00 a. m.», «Miércoles 16», «Hace 15 min»,
  «Septiembre de 2026».

### 8.6 Publicaciones: idempotencia y versiones (`AdminViewModel.kt`)

- **POST** lleva `type` y la cabecera `Idempotency-Key` (UUID). Se reutiliza la
  misma clave mientras el cuerpo no cambie (reintento seguro); si el cuerpo
  cambia, se genera otra.
- **PUT** va **sin `type`** (`Repositorio.editar` lo quita) y con
  `If-Match: "<version>"`. Un 409 `VERSION_CONFLICT` ofrece cargar la versión
  actual.
- Campos ajenos al tipo no se envían: un aviso no lleva `menuDate` y un menú no
  lleva `eventStartAt`.
- `isPinned`: el admin envía el valor del formulario. **El empleado envía el
  valor actual**: si lo omitiera en una publicación fijada por el admin, la API
  responde 403 (comprobado).
- `expiresAt` = inicio del día siguiente al elegido en «Mostrar en el muro
  hasta».

### 8.7 Adjuntos (`Repositorio.subir` / `descargar`)

1. Se elige con el selector del sistema (`OpenDocument`: `image/jpeg`,
   `image/png`, `application/pdf`).
2. Se valida **antes de enviar**: tipo, y tamaño ≤ **5,000,000 bytes**
   (JPEG/PNG) o ≤ **9,000,000 bytes** (PDF).
3. `POST media` multipart (`file` + `altText` opcional con el título). El cuerpo
   es de **un solo envío** (`isOneShot`). OkHttp reintenta por su cuenta un 408,
   y con un archivo de varios MB eso repetiría toda la subida.
4. El `id` devuelto va en `attachmentIds` (máximo 1). Quitar un upload nuevo lo
   borra (`DELETE media/{id}`); quitar uno ya asociado solo lo omite y la API
   lo desasocia.
5. Descarga: siempre con Bearer. Los PDF se guardan en `cache/adjuntos/` y se
   abren con `FileProvider` (`${applicationId}.archivos`); se borran al cerrar
   sesión.

### 8.8 Seguridad

- Solo HTTPS (`base-config cleartextTrafficPermitted="false"`).
- El token no va a logs (`redactHeader("Authorization")`) ni a respaldos.
- Logs de red solo en debug y nunca a nivel BODY (el login lleva la
  contraseña).
- Contraseña y PIN se enmascaran con `PasswordVisualTransformation` sin alterar
  el valor.

---

## 9. Documentación de la API

Fuente completa: `documentacionAPI/openapi.yaml` (OpenAPI 3.1; es JSON válido).

### 9.1 Datos generales

| | |
|---|---|
| Base URL (pruebas) | `https://jmuq4nqote.execute-api.us-east-1.amazonaws.com/api/v1` |
| Base URL (local) | `http://localhost:8080/api/v1` (desde el emulador: `10.0.2.2:8080`) |
| Stack | Node.js 22 + MySQL · ZIP en Elastic Beanstalk detrás de API Gateway HTTP API · RDS MySQL |
| Autenticación | `Authorization: Bearer <accessToken>` en todas las rutas protegidas, **incluidas imágenes y PDF** |
| Token | JWT revocable, 8 h por defecto, sin refresh. Cada login crea una sesión independiente |
| Zona de negocio | America/Mexico_City |
| Correlación | Cabecera `X-Request-Id` (y `apigw-requestid` de Gateway) |
| Rutas públicas | `auth/register`, `auth/login`, `health`, `ready` |

### 9.2 Convenciones de serialización

```json
{ "data": { "id": "123" } }                                        // objeto
{ "data": [], "meta": { "page": 1, "limit": 20, "total": 0 } }     // lista
{ "data": [], "meta": { "page": 1, "limit": 20, "total": 0, "unreadCount": 0 } }  // notificaciones
```

- No existen `data.post`, `data.user` ni `items/pagination`. La excepción es
  el login, que agrupa `user` y los datos de sesión.
- IDs: **String** decimal. Paginación, conteos, `version` y `sizeBytes`:
  números.
- `*At`: ISO 8601 UTC con `Z`. `menuDate` y `admissionDate`: `YYYY-MM-DD`.
- Paginación: `page` ≥ 1 (defecto 1); `limit` entre 1 y 100 (defecto 20).

Error de aplicación:

```json
{ "error": { "code": "VALIDATION_FAILED", "message": "Revise los datos marcados.",
             "requestId": "…", "details": [ { "field": "title", "message": "…" } ] } }
```

`details` puede ser `null`. AWS puede responder con otro cuerpo (p. ej.
`{"message":"Request Timeout"}`); el cliente debe tolerarlo.

Códigos vistos en las pruebas contra AWS: `UNAUTHENTICATED` (401),
`ACCOUNT_PENDING` (403), `FORBIDDEN` (403), `VERSION_CONFLICT` (409),
`IDEMPOTENCY_CONFLICT` (409), `UNSUPPORTED_MEDIA_TYPE` (415) y
`VALIDATION_FAILED` (422). Los demás estados (credencial incorrecta 401, 404,
413, 429 con `Retry-After`) se manejan por status; la app no depende del texto
exacto de `code` salvo `ACCOUNT_PENDING` y `VERSION_CONFLICT`.

### 9.3 Endpoints

Todas las rutas son relativas a `/api/v1`. «Rol» indica quién puede usarla.

**Salud**

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| GET | `/health` | Público | `{data:{status:"ok"}}` |
| GET | `/ready` | Público | `{data:{status:"ready"}}` (conectividad MySQL y esquema) |

**Sesión y perfil propio**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/auth/register` | `{fullName, email, authMethod, secret}` | **201** `{data:User}` con `resident/pending`, **sin token** |
| POST | `/auth/login` | `{email, authMethod:"password"\|"pin", secret, clientType:"android"}` | `{data:{user, expiresAt, tokenType:"Bearer", accessToken}}` |
| GET | `/auth/me` | — | `{data:User}` (con preferencias y perfiles) |
| PATCH | `/auth/me` | `{fullName}` | `{data:User}` |
| POST | `/auth/logout` | — | **204** sin cuerpo; revoca la sesión actual |
| PUT | `/auth/me/credentials` | `{currentSecret, authMethod, secret}` | **204**; revoca las **otras** sesiones. `currentSecret` incorrecto → 422 |
| GET | `/auth/me/preferences` | — | `{data:Preferences}` |
| PUT | `/auth/me/preferences` | `{notifyNotices, notifyActivities, notifyPhotos}` | `{data:Preferences}` |

Reglas: `fullName` 2–150; contraseña 8–128 caracteres; PIN 4–6 dígitos. El
registro nunca acepta `role`, `status` ni perfiles. El login con cuenta
pendiente responde **403 `ACCOUNT_PENDING`**. Un secreto incorrecto no vacío
da 401, aunque sea corto. Un token revocado o vencido da 401. El rol se valida
contra MySQL en cada petición.

**Publicaciones**

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| GET | `/posts` | Todos | Filtros abajo. Por defecto `status=published` |
| POST | `/posts` | Admin, empleado | Crea y **publica** de inmediato; genera notificaciones. Cabecera opcional `Idempotency-Key` (8–128, `[a-zA-Z0-9_-]`). **201** |
| GET | `/posts/{id}` | Según visibilidad | No marca lectura. Devuelve `ETag: "<version>"` |
| PUT | `/posts/{id}` | Autor (empleado) o admin | **Sin `type`**. Cabecera opcional `If-Match: "<version>"` → 409 si cambió. Conserva estado y `publishedAt` |
| DELETE | `/posts/{id}` | Admin | **204**. Permanente: borra adjuntos, lecturas, reacciones y notificaciones. Sin restore |
| PATCH | `/posts/{id}/status` | Autor o admin | `{status:"published"\|"archived"}`. Archivar desfija; republicar no renotifica. Idempotente |
| PATCH | `/posts/{id}/pin` | Admin | `{isPinned:boolean}`. Idempotente |
| PUT | `/posts/{id}/read` | Residente | `{data:{read, readAt}}`. Conserva el primer `readAt` y marca también la notificación |
| PUT | `/posts/{id}/reaction` | Residente | `{type:"like"\|"love"\|"happy"}` → `{data:{counts:{like,love,happy}, total, myReaction}}` |
| DELETE | `/posts/{id}/reaction` | Residente | **200** (no 204) con el mismo resumen |

Parámetros de `GET /posts`:

| Parámetro | Valores | Uso en la app |
|---|---|---|
| `page`, `limit` | 1…, 1–100 | Paginación |
| `status` | `published` (defecto), `draft`, `archived`, `all` | Admin: `all`. El empleado con `all/draft/archived` solo ve lo suyo. El residente recibe 403 |
| `type` | `notice`, `menu`, `event` | Agenda: `event` |
| `category` | Aviso, Actividad, Evento, Noticia (acepta mayúsculas; Salud/Capilla/Comida → Aviso) | Filtros del muro |
| `hasImage` | boolean | Filtro «Fotografías» |
| `pinned` | boolean | — |
| `eventFrom` (inclusivo), `eventTo` (exclusivo) | date-time con Z u offset | Agenda |
| `publishedBefore` | date-time | Archivo |
| `sort` | `-pinned,-publishedAt` (defecto), `-publishedAt`, `eventStartAt` | Agenda: `eventStartAt`; archivo y admin: `-publishedAt` |

Cuerpo de POST (PUT es igual **sin `type`**):

| Campo | Tipo | Aviso | Menú | Evento |
|---|---|:-:|:-:|:-:|
| `type` | `notice`/`menu`/`event` | ✓ | ✓ | ✓ |
| `category` | Aviso/Actividad/Evento/Noticia | ✓ | ✓ | ✓ |
| `title` | 1–150 | ✓ | ✓ | ✓ |
| `body` | 1–5000 | ✓ | ✓ | ✓ |
| `isPinned` | boolean (solo admin puede cambiarlo) | opc. | opc. | opc. |
| `attachmentIds` | `[id]`, máximo 1 | opc. | opc. | opc. |
| `expiresAt` | date-time o null | opc. | opc. | opc. |
| `menuDate` | `YYYY-MM-DD` | — | ✓ | — |
| `mealType` | `breakfast`/`lunch`/`dinner`/`daily` | — | ✓ | — |
| `eventStartAt`, `eventEndAt` | date-time (fin ≥ inicio) | — | — | ✓ |
| `eventLocation` | 1–120 | — | — | ✓ |

Se rechazan los campos ajenos al tipo (422). Repetir la misma
`Idempotency-Key` con el mismo cuerpo y usuario devuelve la respuesta original
durante 24 h; con otro cuerpo → **409 `IDEMPOTENCY_CONFLICT`**.

`Post` (respuesta): `id, type, category, title, body, status, isPinned,
isRead, author{id, fullName, area}, menuDate, mealType, eventStartAt,
eventEndAt, eventLocation, publishedAt, expiresAt, version, attachments[Media],
reactions{counts, total, myReaction}, createdAt, updatedAt`.

**Archivos**

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| POST | `/media` | Admin, empleado | multipart: `file` (obligatorio) y `altText` (≤250). **201** `{data:Media}` con `postId: null`. No enviar base64 en JSON |
| GET | `/media/{id}/content` | Con permiso | Binario con `Content-Type` real y `Cache-Control: private, no-store`. `variant=original` o sin query; **no hay miniaturas** |
| DELETE | `/media/{id}` | Dueño (sin asociar) o admin | **204** |

`Media`: `id, postId, originalName, mimeType, sizeBytes, altText, sortOrder,
contentUrl, createdAt`. `contentUrl` es **relativa al origen**
(`/api/v1/media/45/content`). Límites: JPEG/PNG ≤ 5,000,000 bytes; PDF ≤
9,000,000 bytes (no 10 MB, por el límite total de API Gateway). El servidor
comprueba la firma del archivo (formato falso → 415); no pasa antivirus ni
redimensiona. Los huérfanos de más de 24 h se limpian cada hora.

**Notificaciones (solo residente; personal y admin reciben 403)**

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/notifications?page&limit&read` | `{data:[Notification], meta:{page, limit, total, unreadCount}}`, más recientes primero |
| GET | `/notifications/unread-count` | `{data:{unreadCount}}` |
| PUT | `/notifications/{id}/read` | `{data:Notification}` (no marca el post) |
| POST | `/notifications/read-all` | `{data:{updated, unreadCount}}` |
| POST | `/notifications/archive-read` | `{data:{archived}}` (solo leídas; se conservan en la BD) |

`Notification`: `id, type ("new_post"|"reminder"), title, body, createdAt,
isRead, readAt, postId`. Se generan **al publicar**, en la misma transacción,
para residentes activos según sus preferencias. No se generan al editar ni al
republicar, ni de forma retroactiva al aprobar una cuenta. Clasificación
provisional, sin duplicados:
- Evento con imagen → `notifyPhotos`.
- Evento sin imagen, o categoría Actividad → `notifyActivities`.
- Todo lo demás → `notifyNotices`.

Valores por defecto: avisos sí, actividades sí, fotos no. `reminder` está
reservado: hoy no hay recordatorios automáticos.

**Usuarios (admin)**

| Método | Ruta | Cuerpo / parámetros | Notas |
|---|---|---|---|
| GET | `/users` | `page, limit, role, status, q` | Lista paginada |
| GET | `/users/{id}` | — | `{data:User}` |
| POST | `/users` | `{fullName, email, role, authMethod, secret, residentProfile?, staffProfile?}` | Cuenta **active**, con `approvedBy/approvedAt`. **201** |
| PUT | `/users/{id}` | `{fullName, role, status, residentProfile?, staffProfile?}` | Editar, aprobar o deshabilitar. Sin email ni contraseña. Omitir `residentProfile` lo **conserva**; `null` explícito se rechaza |
| DELETE | `/users/{id}` | — | Alias de deshabilitar (**204**) |
| PUT | `/users/{id}/credentials` | `{authMethod, secret}` | Reposición manual en recepción; **204**; revoca **todas** las sesiones del usuario |

- `residentProfile` de escritura: `department` y `admissionDate` son
  obligatorios. Opcionales: `phone` (10 dígitos), `roomLocation` (≤150) y
  `familyContact{fullName, relationship, phone}`.
- La respuesta añade `roomNumber` (alias de `department`).
- Un perfil incompleto se devuelve como `null`.
- `staffProfile`: `{area}`; la respuesta añade `createdAt` y `lastAccessAt`.
- No se permite auto-deshabilitarse, degradarse, ni quitar al último admin
  activo.
- Observado en pruebas: la API **sí acepta** activar a un residente sin perfil.
  La app es más estricta y exige habitación y fecha de ingreso para confirmar.

`User`: `id, fullName, email, role, status, authMethod, emailVerifiedAt,
approvedBy, approvedAt, createdAt, updatedAt, residentProfile, staffProfile,
preferences`.

**Auditoría (admin)**

| Método | Ruta | Parámetros | Respuesta |
|---|---|---|---|
| GET | `/audit-logs` | `page, limit, actorId, entityType (user/post/media/session/preferences/notification), entityId` | `{data:[{id, actorId, action, entityType, entityId, requestId, createdAt}], meta}` |

Está definida en `ApiServicio.auditoria()`, pero la app **no tiene pantalla**
de auditoría.

**Alias de compatibilidad** (no los usa la app; no se probaron):
`GET/PUT /posts/{id}/read/me`, `GET /posts/{id}/reactions`,
`PUT/DELETE /posts/{id}/reactions/me`.

### 9.4 Matriz de permisos

| Acción | Residente | Empleado | Admin |
|---|:-:|:-:|:-:|
| Ver muro (publicadas, no vencidas) | ✓ | ✓ | ✓ |
| Ver `status=all/draft/archived` | 403 | Solo propias | Todas |
| Crear publicación | 403 | ✓ | ✓ |
| Editar / archivar / republicar | 403 | Solo propias | ✓ |
| Fijar / desfijar | 403 | 403 | ✓ |
| Eliminar permanentemente | 403 | 403 | ✓ |
| Subir adjuntos | 403 | ✓ | ✓ |
| Leer / reaccionar / buzón | ✓ | 403 | 403 |
| Preferencias de notificación | ✓ | (sin buzón) | (sin buzón) |
| Usuarios y auditoría | 403 | 403 | ✓ |

---

## 10. Pruebas

### 10.1 Pruebas unitarias (JVM, sin red)

`./gradlew :app:testDebugUnitTest` — 21 pruebas en `app/src/test/…/data/`.

**`ClienteApiTest.kt`** (MockWebServer, con cuerpos copiados de la evidencia AWS):
- Manda el Bearer y usa rutas relativas; no envía parámetros `null`.
- Un 401 con token cierra la sesión local; el 401 del login no la cierra.
- Lee el error de la aplicación con `details`.
- Tolera el 408 de AWS y el HTML de infraestructura (OkHttp reintenta el 408
  una vez en peticiones normales).
- 429 usa `Retry-After`; el logout 204 no tiene cuerpo.
- El login manda `clientType:"android"`.
- POST lleva `type` e `Idempotency-Key`; PUT va sin `type`, sin nulos y con
  `If-Match`.
- Editar un usuario sin perfil lo omite en vez de mandar `null`.
- `contentUrl` se resuelve contra el origen; la descarga de adjuntos lleva
  Bearer.

**`ModelosTest.kt`**:
- Alias de categorías; conversión de la publicación de la evidencia.
- Un menú puede tener categoría Aviso; los eventos conservan su hora local.
- Rangos de agenda en CDMX; una tzdata vieja con horario de verano cae a UTC-6
  fijo.
- Formato de hora del mockup, extractos, `unreadCount`, perfil y teléfono
  legible.

`SesionEnMemoria.kt` es un `AlmacenSesion` falso para probar sin Android.

### 10.2 Prueba de integración contra la API real

`IntegracionApiTest.kt` recorre la API desplegada **con el mismo cliente de la
app**. Solo corre si existen las variables de entorno; si no, se omite (así no
falla en CI ni sin credenciales):

```bash
RG_API_CORREO='<correo admin de pruebas>' RG_API_SECRETO='<contraseña>' \
  ./gradlew :app:testDebugUnitTest --tests '*IntegracionApiTest' --rerun
# Opcional: RG_API_URL para otra base (por defecto el Gateway de pruebas)
```

- El resultado paso a paso queda en `app/build/integracion-api.txt`.
- Crea datos con prefijo `QA-` y **limpia al final**: borra las publicaciones y
  deshabilita las cuentas creadas. La API no permite borrar usuarios.
- **Nunca** escriba credenciales en archivos del repositorio.

Cubre: salud; login y `auth/me`; preferencias; 403 del buzón para admin; el
registro queda pendiente y su login da 403; lista de pendientes; aprobar con
perfil y conservarlo; alta de empleado con PIN; crear aviso, menú y evento
(horas de CDMX); idempotencia y 409; alias de categoría; PUT con `type` → 422;
versión vieja → 409; fijar, archivar, republicar; subir, descargar y asociar un
PNG; 401 sin token; 415 con formato falso; filtros de fotos, agenda, categoría y
archivo; flujo del residente (lectura, reacción, buzón, leer todas, archivar,
logout); permisos del empleado; reponer credencial; limpieza; logout y token
revocado.

---

## 11. Verificación realizada contra AWS

**23/09/2026, cuenta de administrador de pruebas.**

- **Integración automatizada: 43/43 pasos correctos.**
- **Recorrido manual en emulador (Pixel 4, Android 10):**
  - Login y restauración de sesión.
  - Muro sin pestaña Avisos para admin.
  - Publicar un aviso fijado con foto elegida en el selector del sistema; se ve
    en el muro y en el detalle.
  - Archivar → Deshacer (republica y vuelve a fijar); editar dos veces
    seguidas; eliminar con confirmación.
  - Aprobar desde Usuarios a un residente registrado por la ruta pública; entrar
    con ese residente con PIN.
  - Insignia de buzón, notificación → detalle → lectura, reacciones, perfil y
    preferencias.
- Hallazgos que ya están corregidos o reflejados en el código:
  - **Zona horaria** en teléfonos con tzdata anterior a 2022 (§8.5).
  - **Empleado y `isPinned`**: omitirlo en un aviso fijado da 403; la app envía
    el valor actual (§8.6).
  - **Reintento automático del 408** en subidas grandes: se evita con un cuerpo
    de un solo envío (§8.7).
- Pendiente del backend, según su reporte: el rechazo **413** de archivos
  mayores al límite no está certificado (en AWS se obtuvo 408 por
  transferencia incompleta).

---

## 12. Problemas conocidos y solución de problemas

| Síntoma | Causa / solución |
|---|---|
| `socket failed: EPERM` al entrar, tras `adb install -r` | Estado de permisos heredado de una instalación anterior. `adb uninstall com.residenciaguadalupe.news` e instalar de nuevo |
| «No hay conexión con la residencia» | Sin internet, o el entorno de pruebas está apagado (puede apagarse fuera de las pruebas). Probar `curl …/api/v1/health` |
| Horas corridas una hora | Solo ocurriría si se quitara la protección de `Fechas.zonaResidencia` en un teléfono con tzdata < 2022b |
| `lintVitalAnalyzeRelease` falla con `25.0.3` | JDK 25 + AGP 8.5. Usar JDK 17/21 u omitir lint (ver §3) |
| `Daemon compilation failed` | Mismo origen; Gradle compila igual. `-Pkotlin.compiler.execution.strategy=in-process` |
| Muchos 401 seguidos | El token venció (8 h) o se revocó (logout, cambio o reposición de credencial). La app vuelve sola al acceso |
| 429 al entrar | Rate limit del login: esperar lo que indique el mensaje (`Retry-After`) |
| Imagen no carga | Requiere Bearer: debe cargarse con el `ImageLoader` de la app (Coil configurado en `AplicacionResidencia`) |

Para reportar un problema al backend envíe: método y ruta, rol, JSON sin
secretos, status y respuesta, resultado esperado, hora y **requestId**
(`X-Request-Id` / «Referencia para soporte»). Nunca envíe contraseñas, tokens
ni credenciales de RDS.

---

## 13. Fuera de alcance y pendientes

No forman parte del contrato de la API y **no se implementaron**:

- Visitas familiares (se quitó el botón «Agendar visita familiar»).
- Notificaciones push (FCM): el buzón se consulta al abrir la pantalla.
- Recuperación de contraseña por correo: la repone recepción.
- Recordatorios programados y miniaturas de imágenes.

Pendientes del lado de la app:

- «Escuchar aviso» (lectura en voz alta) solo cambia de estado; falta conectar
  `TextToSpeech`.
- No hay pantalla de auditoría (el endpoint está en `ApiServicio`).
- Dominio HTTPS de producción: hoy release usa el Gateway de pruebas.
- Firma del APK de release.

---

## 14. Documentos de referencia

| Archivo | Contenido |
|---|---|
| `documentacionAPI/REPORTE-FINAL-FRONTEND.md` | Estado del despliegue AWS, base URL, resultados y pendientes del backend |
| `documentacionAPI/ENTREGA-FRONTEND.md` | Contrato de integración: convenciones, decisiones, permisos y reglas de negocio |
| `documentacionAPI/openapi.yaml` | OpenAPI 3.1 con campos, tipos, nulabilidad, requests, responses y filtros |
| `documentacionAPI/POSTMAN.md` + `Residencias-Guadalupe.postman_collection.json` | Colección Postman (35 solicitudes) y recorrido manual recomendado |
| `documentacionAPI/evidencia-pruebas-aws-2026-09-23.json` | Evidencia de pruebas del backend con muestras reales (IDs ya no válidos) |
| `BACKEND_REQUIREMENTS.md` | Requisitos que el frontend pidió al backend antes de la integración (histórico: algunos bloqueadores ya están resueltos) |
