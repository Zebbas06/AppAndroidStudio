# Publicaciones: muro y detalle separados

Este documento registra la extraccion de muro y detalle. La extraccion
posterior de agenda y archivo esta en [Fase 4](FASE_4_AGENDA_Y_ARCHIVO.md);
las referencias a esas responsabilidades en AppViewModel describen el estado
anterior a esa fase. El README contiene el mapa vigente.

## Alcance

Segunda extraccion de funcionalidad de produccion, despues de sesion y
autenticacion. Es una separacion por paquetes dentro del modulo Android
`app`, no nuevos modulos Gradle ni microservicios. No cambia rutas HTTP,
esquema de base de datos, permisos del servidor ni infraestructura AWS.

## Donde esta cada responsabilidad

Dentro de `app/src/main/java/com/residenciaguadalupe/news/`:

```text
publications/
  data/
    RepositorioPublicaciones.kt     contrato, filtros y eventos
    RepositorioPublicacionesApi.kt  HTTP, DTO -> modelo e invalidacion
  feed/
    MuroViewModel.kt                EstadoMuro, filtros, paginas y banda de hoy
    MuroScreen.kt                   presentacion y acciones del muro
  detail/
    DetalleViewModel.kt             EstadoDetalle, lectura, reaccion y descarga
    DetalleScreen.kt                presentacion y apertura del archivo
```

Los estados son valores inmutables expuestos como `StateFlow` de solo
lectura. La pantalla no recibe listas mutables ni puede modificar el estado.
Los dos ViewModels son `ViewModel` normales, sin dependencia de
`Application`, `Context` ni Retrofit. Se prueban con un repositorio falso.
Detalle recibe una funcion de descarga; el acceso al archivo y al
`ContentResolver` sigue en el repositorio anterior.

`AplicacionResidencia` crea una sola instancia de `RepositorioPublicaciones`.
`Navegacion` construye los ViewModels mediante fabricas:
muro mantiene filtros y paginas al cambiar de pestaña; detalle pertenece a
su entrada de navegacion y se cancela al dejar de mostrarse.

## Como se comunican

```text
MuroScreen -> MuroViewModel ----+
DetalleScreen -> DetalleVM ----+--> RepositorioPublicaciones -> ApiServicio
Agenda / Archivo / Admin ------+                             -> API existente

escritura administrativa exitosa -> cambios (revision)
                                -> muro recarga al volver

lectura / reaccion exitosa -> actualizaciones (evento por publicacion)
                          -> muro actualiza solo esa fila
                          -> AppViewModel sincroniza notificacion leida y contador

GestorSesion -> cancela tareas y emite nueva revision
             -> cada ViewModel limpia su propio estado
```

`cambios` es un contador persistente en memoria: aunque el muro no se este
mostrando, al regresar puede detectar una escritura. `actualizaciones` es un
flujo de eventos, sin replay ni cache de publicaciones: no reemplaza al
servidor como fuente de verdad. Al cargar una pantalla se consultan sus datos.
Los observers se ejecutan en `viewModelScope`; las operaciones de red usan
`GestorSesion.lanzar` para poder cancelarse entre cuentas.

## Que se retiro del codigo anterior

- `AppViewModel`: listas, filtros, paginacion y banda del muro; detalle,
  reacciones, descarga de detalle e indicador de voz.
- `Repositorio`: consulta y escritura de publicaciones, filtros e invalidacion.
- `ui/pantallas`: muro y detalle; ahora viven junto al codigo de su funcionalidad.

Agenda y archivo permanecen en `AppViewModel`, pero consultan el repositorio
de publicaciones. Administracion usa ese mismo contrato para publicar,
editar, fijar, archivar y eliminar; sus subidas siguen usando `Repositorio`.
No se conservaron dos implementaciones del mismo servicio.

## Correcciones incluidas

- Abrir B cancela las tareas de A. Un identificador de solicitud impide que
  respuestas o bloques `finally` anteriores sobrescriban B o apaguen su carga.
- Cambiar de filtro cancela la pagina anterior y conserva el estado de carga
  de la nueva solicitud. La paginacion elimina duplicados por ID.
- Salir de detalle cancela tambien la descarga: un PDF tardio no abre otra app.
- Lectura y reacciones actualizan el muro mediante eventos, no referencias
  directas a otro ViewModel.
- Un fallo al registrar lectura mantiene visible el contenido y muestra un
  aviso, sin marcarlo localmente como leido.
- La banda de hoy distingue carga, error y ausencia real de actividades;
  ofrece reintento ante un error.
- Cambiar de cuenta limpia contenido, filtros, errores y acciones pendientes.

## Ventaja para mantenimiento

| Sintoma | Archivo que revisar primero |
| --- | --- |
| Filtro, duplicados o paginacion | publications/feed/MuroViewModel.kt |
| Resumen de hoy o su presentacion | publications/feed/MuroViewModel.kt y MuroScreen.kt |
| Lectura, reaccion o respuesta tardia | publications/detail/DetalleViewModel.kt |
| PDF abre fuera de contexto | DetalleViewModel.kt; luego Repositorio.descargar |
| Query, conversion o contrato de publicaciones | publications/data/RepositorioPublicacionesApi.kt |
| Regreso o instancia de detalle incorrecta | Navegacion.kt |
| Lectura no actualiza el buzon | AppViewModel: observer de actualizaciones |

Para una nueva operacion de publicaciones: agregarla al contrato, traducirla
en la implementacion API, actualizar el estado en el ViewModel responsable y
probar exito, fallo y cancelacion. La pantalla se limita a mostrar el estado
y emitir la accion; no realiza HTTP.

## Verificacion

Comando local, sin modificar AWS:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:verificarBase --offline -Pkotlin.compiler.execution.strategy=in-process
```

Resultado del 5 de octubre de 2026: APK debug compilado y 58 pruebas locales
aprobadas. Esta extraccion agrega 20 pruebas:

- 7 de muro: filtros, paginas, respuestas tardias, banda, eventos, invalidacion y cuenta.
- 8 de detalle: lectura, permisos, respuestas tardias, reaccion, PDF, errores y cierre.
- 5 de repositorio: filtros HTTP, edicion con If-Match, errores e intercambio de eventos.

No se ejecuto integracion remota, ni pruebas visuales en emulador o dispositivo.
Pendiente validar manualmente acceso -> muro -> detalle -> regreso, abrir
detalle desde agenda/buzon/archivo, reacciones, PDF, publicar desde
administracion y cerrar/abrir sesion con otra cuenta.

## Limites y siguiente paso

La remodelacion no esta terminada. `AppViewModel` conserva acceso, perfil,
agenda, archivo y notificaciones. `Repositorio` conserva medios,
notificaciones y usuarios. DTOs y modelos siguen compartidos en `data`.

El boton de voz conserva el comportamiento anterior: solo cambia un
indicador; no se implemento TextToSpeech en esta extraccion. Sigue pendiente,
igual que la aprobacion de usuarios, las preferencias concurrentes y las
subidas de distintos formularios.

Siguiente extraccion recomendada: agenda y archivo, aprovechando este mismo
repositorio; despues notificaciones, perfil, usuarios y medios. Crear
modulos Gradle separados tiene sentido cuando existan limites estables y una
necesidad real de aislamiento de compilacion, no solo para multiplicar carpetas.
