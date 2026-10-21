# Residencia Guadalupe News — app Android

Aplicación Android (Kotlin + Jetpack Compose) del mural de avisos de la
Residencia Guadalupe A.C. (Cuernavaca). Los residentes leen avisos, menús y
eventos, reciben notificaciones y reaccionan; el personal publica y administra
avisos; la administración además aprueba residentes y gestiona cuentas.

La app está **conectada a la API REST del proyecto** (entorno de pruebas). Ya
no usa datos de ejemplo.

> Entorno de **pruebas** con datos ficticios: no es una certificación de
> producción ni autoriza cargar datos personales reales de residentes.

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
11. [Verificación realizada contra el entorno de pruebas](#11-verificación-realizada-contra-el-entorno-de-pruebas)
12. [Problemas conocidos y solución de problemas](#12-problemas-conocidos-y-solución-de-problemas)
13. [Fuera de alcance y pendientes](#13-fuera-de-alcance-y-pendientes)
14. [Documentos de referencia](#14-documentos-de-referencia)

---

## 1. Estado actual

La remodelacion comienza con una base de verificacion: consulte
[Fase 1: pruebas y recorridos por rol](docs/remodelacion/FASE_1_BASE_DE_VERIFICACION.md).
`./gradlew :app:verificarBase` compila debug y ejecuta las pruebas locales.
Las pruebas contra AWS requieren habilitacion explicita y se ejecutan aparte.

La primera extraccion de codigo ya separa sesion y autenticacion. Consulte
[Sesion y autenticacion separadas](docs/remodelacion/FASE_2_SESION_Y_AUTENTICACION.md)
para responsabilidades, flujo de cierre y limites de verificacion.
La siguiente extraccion separa muro y detalle por funcionalidad:
[Publicaciones, muro y detalle](docs/remodelacion/FASE_3_PUBLICACIONES.md).
Agenda y archivo tambien tienen su propio estado y ViewModel:
[Agenda y archivo separados](docs/remodelacion/FASE_4_AGENDA_Y_ARCHIVO.md).
El buzon y su insignia tambien tienen propietario independiente:
[Notificaciones y contador](docs/remodelacion/FASE_5_NOTIFICACIONES.md).
Perfil y preferencias ya tienen su propio estado y guardado ordenado:
[Perfil y preferencias separados](docs/remodelacion/FASE_6_PERFIL_Y_PREFERENCIAS.md).
Usuarios y aprobacion ahora tienen repositorio y formulario aislados:
[Usuarios y aprobacion](docs/remodelacion/FASE_7_USUARIOS_Y_APROBACION.md).
Lista administrativa, editor y medios ya tienen responsabilidades independientes:
[Editor y medios separados](docs/remodelacion/FASE_8_EDITOR_Y_MEDIOS.md).
Acceso y arranque cierran la segmentacion estructural prevista:
[Acceso y arranque separados](docs/remodelacion/FASE_9_ACCESO_Y_ARRANQUE.md).
Faltan los recorridos integrales en dispositivo; esto no certifica produccion.

| Área | Estado |
|---|---|
| Sesión (login contraseña/PIN, registro, restaurar, logout, 401) | Acceso y arranque separados; pruebas locales aprobadas |
| Muro, filtros, paginación, banda «Hoy en la residencia» | Conectado y verificado |
| Agenda (Hoy / Mañana / Esta semana) | Conectado y verificado |
| Detalle, lectura, reacciones, adjuntos (imagen/PDF) | Conectado y verificado |
| Notificaciones (buzón, contador, leer, leer todas, archivar leídas) | Conectado y verificado |
| Perfil (datos, preferencias, nombre, credencial, tamaño de letra) | Separado; guardado ordenado y pruebas locales aprobadas |
| Administración de avisos (crear, editar, fijar, archivar, eliminar) | Lista y editor separados; pruebas locales aprobadas |
| Usuarios (pendientes, aprobar, alta, editar, deshabilitar, reponer) | Separado; aprobacion confirmada y pruebas locales aprobadas |
| Pruebas unitarias | 188 pruebas locales aprobadas; ver remodelacion para cobertura |
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
2. Agregue `rg.api.url` a `local.properties` (ver [sección 4](#4-configuración-url-de-la-api-y-variantes)).
3. Espere el *Gradle sync*. Solo descarga dependencias de Google y Maven Central.
4. **Run** en un emulador o teléfono con API 24 o superior y acceso a internet.

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
| Base de la remodelacion | `./gradlew :app:verificarBase` (APK debug + pruebas locales) |
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

La URL **no se versiona**. `app/build.gradle.kts` la lee de `local.properties`
(que ya está en `.gitignore`) y la entrega al código como `BuildConfig.URL_API`:

```properties
rg.api.url=https://<direccion-de-la-api>/api/v1/
```

También se acepta la variable de entorno `RG_API_URL`. Si falta, o si no usa
`https://` y barra final, Gradle se detiene con un mensaje. La dirección real
se comparte con el equipo por un canal privado; no la copie en documentación
pública, issues ni capturas.

| Variante | URL | Tráfico HTTP en claro | Logs de red |
|---|---|---|---|
| `debug` | API de pruebas (HTTPS) | Solo a `10.0.2.2` (API local desde el emulador) | `HttpLoggingInterceptor` nivel BASIC, cabecera `Authorization` oculta |
| `release` | API de pruebas (HTTPS) mientras no exista dominio de producción | Prohibido | Ninguno |

Reglas:

- **Usar siempre la dirección pública de la API**, nunca un host interno del
  servidor ni la base de datos.
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
        │   │   ├── app/                      ← Navegacion y ArranqueViewModel (sin formulario)
        │   │   ├── media/data/              ← repositorio de medios, fuente Android y lectura limitada
        │   │   ├── users/                   ← UsuariosScreen, UsuariosViewModel y data/RepositorioUsuarios
        │   │   ├── core/session/            ← modelo, persistencia y GestorSesion
        │   │   ├── auth/
        │   │   │   ├── AccesoScreen.kt       ← formulario y credenciales solo en memoria de UI
        │   │   │   ├── AccesoViewModel.kt    ← envio, errores, pendiente y confirmacion
        │   │   │   ├── data/RepositorioAutenticacion.kt ← interfaz y adaptador API
        │   │   │   └── domain/              ← ValidarAcceso y ValidarCredencial
        │   │   ├── profile/
        │   │   │   ├── data/RepositorioPerfil.kt ← perfil propio, preferencias y sesion actual
        │   │   │   ├── PerfilViewModel.kt      ← estado y guardado ordenado
        │   │   │   └── PerfilScreen.kt         ← datos, formularios y accesibilidad
        │   │   ├── notifications/
        │   │   │   ├── data/                 ← contrato y repositorio HTTP del buzon
        │   │   │   ├── NotificacionesViewModel.kt ← lista, insignia, lecturas y archivado
        │   │   │   └── AvisosScreen.kt        ← presentacion del buzon
        │   │   ├── publications/
        │   │   │   ├── data/                 ← contrato y repositorio HTTP de publicaciones
        │   │   │   ├── feed/                 ← MuroScreen, MuroViewModel y EstadoMuro
        │   │   │   ├── detail/               ← DetalleScreen, DetalleViewModel y EstadoDetalle
        │   │   │   ├── agenda/               ← HoyScreen, AgendaViewModel y EstadoAgenda
        │   │   │   ├── archive/              ← ArchivoScreen, ArchivoViewModel y EstadoArchivo
        │   │   │   ├── administration/       ← lista, acciones y Deshacer
        │   │   │   └── editor/               ← formulario, validacion, uploads y publicar
        │   │   ├── data/
        │   │   │   ├── Modelos.kt            ← modelos de UI y conversión desde DTOs
        │   │   │   ├── Fechas.kt             ← zona America/Mexico_City y textos de fecha
        │   │   │   ├── red/
        │   │   │   │   ├── ApiServicio.kt    ← interfaz Retrofit (todas las operaciones)
        │   │   │   │   ├── ClienteApi.kt     ← Json, OkHttp con Bearer, Retrofit, URL de adjuntos
        │   │   │   │   ├── Dtos.kt           ← DTOs del contrato OpenAPI
        │   │   │   │   ├── Envolturas.kt     ← {data}, {data,meta}, {error}
        │   │   │   │   └── ErrorApi.kt       ← traducción de errores y llamar()/llamarSinCuerpo()
        │   │   │   └── repositorio/Pagina.kt     ← modelo de pagina compartido
        │   │   └── ui/
        │   │       ├── theme/ (Color.kt, Type.kt)   ← paleta y tipografía del mockup
        │   │       └── componentes/                  ← Comunes.kt, BarraInferior.kt, AvisoEmergente.kt
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
Pantallas Compose (publications/*, notifications, profile, users y auth)
        │  leen estado observable (StateFlow o mutableStateOf) y llaman métodos
        ▼
MuroViewModel · AgendaViewModel · NotificacionesViewModel · PerfilViewModel · UsuariosViewModel (Activity)
DetalleViewModel · ArchivoViewModel · AdminPublicacionesViewModel · EditorPublicacionViewModel · AccesoViewModel (entrada de navegacion)
ArranqueViewModel (Activity; restauracion y sesion del grafo)
        │  capturan ErrorApi y lo convierten en estado de pantalla
        ▼
RepositorioPublicaciones / RepositorioNotificaciones / RepositorioPerfil / RepositorioUsuarios
RepositorioAutenticacion (acceso, restauracion y logout)
RepositorioMedios (archivos del selector, subida y cache privada)
        │
        ▼
ClienteApi: OkHttp (Bearer + cierre ante 401) → Retrofit + kotlinx.serialization → ApiServicio
        │                                      └── Coil reutiliza el mismo OkHttp
        ▼
API REST (HTTPS)
```

- **Contenedor manual.** `AplicacionResidencia` crea de forma perezosa
  `GestorSesion`, `AlmacenSesionPreferencias`, `ClienteApi`, los repositorios y el `ImageLoader`
  de Coil. Los ViewModels de publicaciones y notificaciones reciben dependencias por constructor mediante las
  fabricas de `Navegacion`; perfil recibe sus dependencias en `MainActivity`
  para aplicar la escala a toda la interfaz. Los ViewModels anteriores aun usan `Application`.
- **Ciclo de sesion.** `GestorSesion` cancela trabajos y ejecuta la limpieza
  compartida. Los ViewModels y la navegacion observan su revision para
  reiniciar sus estados entre cuentas.
- **Estado.** Publicaciones, notificaciones y perfil exponen `StateFlow` de un estado inmutable con
  listas de solo lectura. Los ViewModels anteriores mantienen el patron
  Compose. Las pantallas guardan solo estado visual, no datos de red.
- **Errores.** Toda llamada pasa por `llamar { }` o `llamarSinCuerpo { }`
  (`ErrorApi.kt`), que devuelven el cuerpo o lanzan `ErrorApi` con un mensaje
  que siempre se puede mostrar al usuario.
- **Recarga del muro.** `RepositorioPublicaciones.cambios` sube tras cada
  escritura administrativa exitosa. `MuroViewModel` recarga al regresar.
  `actualizaciones` comunica lecturas y reacciones sin acoplar ViewModels.
- **Detalle aislado.** Cada entrada de navegacion tiene su ViewModel;
  cambiar de publicacion o salir cancela lectura, reaccion y descarga.
  Las respuestas solo se aplican a la solicitud que sigue vigente.
- **Agenda y archivo.** Conservan paginas al regresar de detalle. Al volver
  o solicitar otra pagina detectan cambio de dia o revision administrativa
  y recargan sin mezclar rangos. Un error de pagina conserva las filas y
  permite reintentar la misma pagina.
- **Buzon e insignia.** `NotificacionesViewModel` mantiene ambos y observa
  lecturas de publicaciones. Las operaciones usan un Mutex para ordenar
  snapshots y escrituras; solo se confirma una lectura aceptada por la API.
  Archivar recarga pagina 1 porque cambian los offsets. Perder la sesion o
  el permiso de residente limpia contenido y cancela tareas.
- **Perfil.** Las preferencias se guardan por un unico worker: mantiene la
  eleccion mas reciente mientras confirma snapshots en serie. Un fallo del
  ultimo envio restaura el ultimo estado confirmado, no una copia obsoleta.
  Nombre, credencial y preferencias actualizan la sesion actual; una cuenta
  nueva nunca recibe respuestas de la anterior. La escala sigue siendo local.

---

## 7. Pantallas, navegación y roles

### 7.1 Rutas (`app/Navegacion.kt`)

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
| `admin_crear?postId={postId}` | Crear / editar aviso (ID opcional) | `AdminCrearScreen.kt` | Admin y empleado |
| `usuarios` | Lista de usuarios | `UsuariosScreen.kt` | Solo admin |
| `usuario/{id}` | Alta / edición / aprobación (`id = nuevo` para alta) | `UsuariosScreen.kt` | Solo admin |

### 7.2 Flujo de arranque y sesión

1. Mientras `ArranqueViewModel.estado.restaurando` es `true` se muestra «Abriendo el mural…».
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

### 8.1 Sesión (`core/session`)

- Se guarda `token`, `vence_en` (ISO) y el `usuario` (JSON) en
  SharedPreferences privadas **`sesion_residencia.xml`**.
- Ese archivo está **excluido del respaldo en la nube y de la transferencia
  entre dispositivos** (`res/xml/reglas_respaldo.xml`, `reglas_extraccion.xml`).
- `GestorSesion` expone `StateFlow<EstadoSesion>` con sesion, revision y motivo.
  Los ViewModels lo observan; la persistencia no coordina la interfaz.
- No hay refresh token: al vencer (8 h) el usuario vuelve a entrar.
- Al cerrar sesion se cancelan trabajos y se borran sesion local, PDFs e
  imagenes en memoria. Luego se intenta `POST auth/logout` en segundo plano
  con el token anterior. El cierre local no espera a internet.

### 8.2 Cliente HTTP (`data/red/ClienteApi.kt`)

- Timeouts: conexión 20 s, lectura 30 s, escritura 60 s (subidas lentas).
- **Interceptor de token**:
  - Añade `Authorization: Bearer <token>` solo a peticiones con el mismo
    esquema, host y puerto de la API.
  - Ante un **401** en una petición que llevaba token (salvo `auth/login`),
    informa al gestor para cerrar y limpiar con el motivo «Su sesión terminó».
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
  `InterceptorImagenSesion` separa claves por revision y descarta respuestas
  que terminan despues de un cambio de sesion.

### 8.3 Errores (`data/red/ErrorApi.kt`)

`ErrorApi(estado, codigo, mensaje, referencia, detalles, reintentarEn)`:

- Cuerpo de la aplicación `{error:{code,message,requestId,details}}` → se usa
  `message` tal cual, más los mensajes por campo (`mensajeCompleto`,
  `mensajeDe(campo)`).
- Cuerpo de infraestructura (p. ej. `{"message":"Request Timeout"}` o
  HTML) → mensaje propio según el status; **nunca se muestra HTML ni texto
  técnico**.
- `referencia` = `requestId` del cuerpo o la cabecera de correlación de la
  respuesta. Se muestra como «Referencia para soporte» en errores
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
- Los **IDs son `String`** siempre (enteros grandes del servidor).

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

### 8.6 Publicaciones: idempotencia y versiones (`publications/editor/EditorPublicacionViewModel.kt`)

- **POST** lleva `type` y la cabecera `Idempotency-Key` (UUID). Se reutiliza la
  misma clave mientras el cuerpo no cambie (reintento seguro); si el cuerpo
  cambia, se genera otra.
- **PUT** va **sin `type`** (`RepositorioPublicacionesApi.editar` lo quita) y con
  `If-Match: "<version>"`. Un 409 `VERSION_CONFLICT` ofrece cargar la versión
  actual.
- Campos ajenos al tipo no se envían: un aviso no lleva `menuDate` y un menú no
  lleva `eventStartAt`.
- `isPinned`: el admin envía el valor del formulario. **El empleado envía el
  valor actual**: si lo omitiera en una publicación fijada por el admin, la API
  responde 403 (comprobado).
- `expiresAt` = inicio del día siguiente al elegido en «Mostrar en el muro
  hasta».

### 8.7 Adjuntos (`media/data/RepositorioMediosApi.kt`)

1. Se elige con el selector del sistema (`OpenDocument`: `image/jpeg`,
   `image/png`, `application/pdf`).
2. Se valida **antes de enviar**: tipo, y tamaño ≤ **5,000,000 bytes**
   (JPEG/PNG) o ≤ **9,000,000 bytes** (PDF). La lectura se detiene al exceder
   el limite aunque el proveedor omita o falsee el tamaño.
3. `POST media` multipart (`file` + `altText` opcional con el título). El cuerpo
   es de **un solo envío** (`isOneShot`). OkHttp reintenta por su cuenta un 408,
   y con un archivo de varios MB eso repetiría toda la subida.
4. El `id` devuelto va en `attachmentIds` (máximo 1). Quitar un upload nuevo
   que nunca se envio al publicar intenta borrarlo (`DELETE media/{id}`). Si ya
   participo en un envio sin confirmar, no se borra: puede estar asociado en el
   servidor. Quitar uno existente lo omite y la API lo desasocia al guardar.
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

La especificación completa (OpenAPI, colección de Postman y evidencias de
pruebas) se entrega al equipo por separado en `documentacionAPI/`, carpeta
excluida del repositorio. No publique aquí la URL del entorno, el catálogo de
rutas, credenciales ni detalles de la infraestructura del servidor.

Lo que la app asume del contrato:

- Autenticación con `Authorization: Bearer <token>` en las rutas protegidas,
  incluidas imágenes y PDF.
- Respuestas `{data}`, `{data, meta}` en listas paginadas y
  `{error:{code, message, requestId, details}}` en errores.
- IDs como `String`; instantes `*At` en ISO 8601 UTC; fechas civiles
  `YYYY-MM-DD`; zona de negocio America/Mexico_City.

---

## 10. Pruebas

### 10.1 Pruebas unitarias (JVM, sin red)

`./gradlew :app:testDebugUnitTest` — 188 pruebas locales en `app/src/test/`.
La integracion remota queda excluida salvo habilitacion explicita.

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

`publications/` agrega 20 pruebas: contrato HTTP del repositorio, filtros,
paginacion, errores de actividades, eventos de lectura/reaccion, limpieza de
cuenta y respuestas tardias de detalle, reacciones y PDFs.
`AgendaViewModelTest` y `ArchivoViewModelTest` agregan otras 16 pruebas:
rangos compartidos con la banda del muro, cambios rapidos de pestaña,
paginacion, reintentos, agrupacion mensual, cambio de dia e invalidacion.
`notifications/` agrega 18 pruebas: 14 de buzon, insignia, concurrencia,
lecturas, archivado y permisos; 4 del contrato HTTP.
`profile/` agrega 12 pruebas de ViewModel y 5 de contrato y persistencia;
`auth/domain/ValidarCredencialTest` agrega 3 de reglas compartidas.
`users/UsuariosViewModelTest` agrega 9 de aprobacion, permisos, credenciales,
paginacion y respuestas tardias.
La fase 8 agrega 40 pruebas: 14 de editor, 8 de lista administrativa,
5 de reglas del formulario y 13 de medios, contrato HTTP y lectura limitada.
La fase 9 agrega 27 pruebas: 11 de acceso, 10 de arranque, 4 de validacion
publica y 2 adicionales del contrato HTTP de autenticacion.

`SesionEnMemoria.kt` es un `AlmacenSesion` falso para probar sin Android.

`core/session/GestorSesionTest.kt` y `auth/data/RepositorioAutenticacionTest.kt`
comprueban las transiciones, cancelacion, limpieza y revocacion capturada.

**`SesionTest.kt`** comprueba que una sesion sigue vigente antes de su
vencimiento y deja de ser valida exactamente en ese instante.

La fase 1 agrega pruebas de respuesta incompatible, adjuntos de otro host y
401 de una sesion anterior sin cerrar un login nuevo. Consulte los recorridos
y limites de cobertura en [la base de verificacion](docs/remodelacion/FASE_1_BASE_DE_VERIFICACION.md).

### 10.2 Prueba de integración contra la API real

`IntegracionApiTest.kt` recorre la API desplegada **con el mismo cliente de la
app**. Se excluye de las pruebas ordinarias. Para habilitarla expresamente
se requiere `-PintegracionApi=true` y las variables de entorno; la tarea falla
si se habilita sin credenciales:

```bash
RG_API_CORREO='<correo admin de pruebas>' RG_API_SECRETO='<contraseña>' \
  ./gradlew :app:testDebugUnitTest -PintegracionApi=true --tests '*IntegracionApiTest' --rerun-tasks
# Opcional: RG_API_URL para otra base (por defecto rg.api.url de local.properties)
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

## 11. Verificación realizada contra el entorno de pruebas

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

---

## 12. Problemas conocidos y solución de problemas

| Síntoma | Causa / solución |
|---|---|
| `socket failed: EPERM` al entrar, tras `adb install -r` | Estado de permisos heredado de una instalación anterior. `adb uninstall com.residenciaguadalupe.news` e instalar de nuevo |
| «No hay conexión con la residencia» | Sin internet, o el entorno de pruebas está apagado (puede apagarse fuera de las pruebas). Consultar con el equipo de backend |
| Horas corridas una hora | Solo ocurriría si se quitara la protección de `Fechas.zonaResidencia` en un teléfono con tzdata < 2022b |
| `lintVitalAnalyzeRelease` falla con `25.0.3` | JDK 25 + AGP 8.5. Usar JDK 17/21 u omitir lint (ver §3) |
| `Daemon compilation failed` | Mismo origen; Gradle compila igual. `-Pkotlin.compiler.execution.strategy=in-process` |
| Muchos 401 seguidos | El token venció (8 h) o se revocó (logout, cambio o reposición de credencial). La app vuelve sola al acceso |
| 429 al entrar | Rate limit del login: esperar lo que indique el mensaje (`Retry-After`) |
| Imagen no carga | Requiere Bearer: debe cargarse con el `ImageLoader` de la app (Coil configurado en `AplicacionResidencia`) |

Para reportar un problema al backend envíe: método y ruta, rol, JSON sin
secretos, status y respuesta, resultado esperado, hora y **requestId**
(`X-Request-Id` / «Referencia para soporte»). Nunca envíe contraseñas, tokens
ni credenciales de la base de datos.

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
- Dominio HTTPS de producción: hoy release usa el entorno de pruebas.
- Firma del APK de release.

---

## 14. Documentos de referencia

| Archivo | Contenido |
|---|---|
| `documentacionAPI/REPORTE-FINAL-FRONTEND.md` | Estado del despliegue, resultados y pendientes del backend |
| `documentacionAPI/ENTREGA-FRONTEND.md` | Contrato de integración: convenciones, decisiones, permisos y reglas de negocio |
| `documentacionAPI/openapi.yaml` | OpenAPI 3.1 con campos, tipos, nulabilidad, requests, responses y filtros |
| `documentacionAPI/POSTMAN.md` + `Residencias-Guadalupe.postman_collection.json` | Colección Postman (35 solicitudes) y recorrido manual recomendado |
| `documentacionAPI/evidencia-pruebas-aws-2026-09-23.json` | Evidencia de pruebas del backend |
| `BACKEND_REQUIREMENTS.md` | Requisitos que el frontend pidió al backend antes de la integración (histórico: algunos bloqueadores ya están resueltos) |
