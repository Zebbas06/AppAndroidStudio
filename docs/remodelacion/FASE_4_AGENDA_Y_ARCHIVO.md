# Agenda y archivo separados

Este documento registra el estado al extraer agenda y archivo.
El buzon descrito como pendiente ya fue extraido en
[Fase 5: notificaciones](FASE_5_NOTIFICACIONES.md). El README contiene el mapa vigente.

## Alcance

Agenda y archivo dejan de pertenecer a `AppViewModel`. Conservan el mismo
repositorio de publicaciones, cliente HTTP, contrato API y permisos.
No se modifican AWS ni la base de datos. La division es por funcionalidad
dentro del modulo Android `app`, sin agregar dependencias.

## Distribucion

Dentro de `app/src/main/java/com/residenciaguadalupe/news/`:

```text
publications/
  data/
    FiltroAgenda.kt       rango compartido por agenda y banda del muro
  agenda/
    AgendaViewModel.kt    EstadoAgenda, pestana, carga y paginacion
    HoyScreen.kt          presentacion de la agenda
  archive/
    ArchivoViewModel.kt   EstadoArchivo, paginas y agrupacion por mes
    ArchivoScreen.kt      presentacion de publicaciones anteriores
```

Los dos ViewModels reciben `RepositorioPublicaciones`, `GestorSesion` y
una funcion de fecha por constructor. No dependen de Android Context,
Application ni Retrofit. Sus estados se exponen como `StateFlow` de solo
lectura, con listas inmutables desde la perspectiva de la pantalla.

`Navegacion` crea las instancias con fabricas:
- Agenda tiene alcance Activity y conserva la pestana al cambiar de destino.
- Archivo pertenece a su entrada de navegacion. Abrir detalle y volver
  conserva sus paginas; al retirar la entrada, su ViewModel y tareas se liberan.

La persistencia es solo en memoria, no tras matar el proceso. La restauracion
con SavedStateHandle queda para una fase posterior.

## Flujo y responsabilidades

```text
HoyScreen -> AgendaViewModel ------+
                                  +-> RepositorioPublicaciones -> API existente
ArchivoScreen -> ArchivoViewModel +

GestorSesion -> revision y cancelacion -> limpieza de cada ViewModel
RepositorioPublicaciones.cambios -> comparacion al entrar o cargar otra pagina
```

Las pantallas muestran carga, vacio, contenido y error; emiten acciones.
El ViewModel decide pagina, rango, reintento y actualizacion de estado.
El repositorio traduce consultas y respuestas; no agrupa meses ni administra
pestanas. Ninguno de estos ViewModels escribe en `AppViewModel`.

## Correcciones y mejoras

- Al cambiar de pestana se limpian las filas anteriores. La fecha seleccionada
  no se muestra junto a actividades que pertenecen a otro rango.
- Cada carga tiene identificador de solicitud y cancela la anterior; una
  respuesta o `finally` tardios no reemplazan datos ni apagan la nueva carga.
- La agenda deja de ignorar silenciosamente eventos posteriores a los primeros
  100: ofrece cargar paginas adicionales, segun `meta.total`.
- Agenda y muro usan el mismo filtro temporal: dias con fin exclusivo y semana
  de lunes a domingo, con limites locales de Mexico_City convertidos a UTC.
- Ambas listas eliminan duplicados por ID.
- Un fallo de pagina conserva las filas, muestra el error junto al control y
  reintenta la misma pagina; el contador avanza solo despues de una respuesta
  exitosa. No depende del aviso global de otra funcionalidad.
- Volver de detalle no reinicia innecesariamente el archivo. Un nuevo dia o
  revision administrativa si reinicia desde pagina 1.
- Las paginas de una carga usan el mismo corte temporal. Si cambia el dia o
  hay escritura administrativa, la siguiente accion reinicia la consulta.
- Cambiar o cerrar sesion cancela tareas y limpia listas, filtros, errores y
  paginacion en cada funcionalidad.

El archivo es el historial publicado antes de hoy, no una consulta de
`status=archived`. Se mantiene `publishedBefore`, orden `-publishedAt`,
agrupacion por mes y grupo "Sin fecha" para datos incompletos.

## Beneficio para mantenimiento

`AppViewModel` pasa de 463 a 365 lineas en esta extraccion. Conserva sesion,
acceso, perfil, preferencias, notificaciones y sus avisos generales.
La reduccion no es el objetivo por si misma: cada comportamiento extraido
ahora tiene estado, pantalla y pruebas identificables.

| Problema | Donde buscar primero |
| --- | --- |
| Pestana, carga o paginacion de actividades | publications/agenda/AgendaViewModel.kt |
| Rangos distintos entre muro y agenda | publications/data/FiltroAgenda.kt |
| Presentacion o interaccion de la agenda | publications/agenda/HoyScreen.kt |
| Pagina repetida, reintento o corte del archivo | publications/archive/ArchivoViewModel.kt |
| Agrupacion mensual | EstadoArchivo.meses |
| Presentacion del archivo | publications/archive/ArchivoScreen.kt |
| Estado perdido al regresar | Navegacion.kt y alcance del ViewModel |
| Query o conversion HTTP | publications/data/RepositorioPublicacionesApi.kt |

## Verificacion

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:verificarBase --offline -Pkotlin.compiler.execution.strategy=in-process
```

Resultado del 5 de octubre de 2026: APK debug compilado y 74 pruebas locales
aprobadas, sin fallos ni pruebas omitidas entre las suites locales.
La integracion AWS sigue excluida expresamente.

Se agregaron 16 pruebas:
- 8 de agenda: rangos y cambio de anio, pestana y respuesta tardia, paginas,
  reintento, fallo inicial, regreso y nuevo dia, revision administrativa y cuenta.
- 8 de archivo: filtro, grupos y fechas ausentes, paginas sin duplicados,
  reintento, recarga contra pagina tardia, regreso, nuevo dia y cierre de sesion.

No se ejecuto validacion visual en emulador/dispositivo ni integracion remota.
Recorridos manuales pendientes:
- Hoy -> Manana -> Semana rapidamente: comprobar fechas y actividades.
- Agenda -> detalle -> regresar: conservar pestana y listado.
- Archivo -> cargar pagina -> detalle -> regresar: conservar paginas.
- Fallo de una pagina -> reintentar: conservar las filas existentes.
- Administracion modifica una publicacion -> regresar a agenda/archivo.
- Cerrar sesion y entrar con otra cuenta: ninguna fila anterior permanece.
- Revisar letra grande, TalkBack y controles de paginacion.

## Limites y siguiente paso

La fecha y la revision se comprueban al entrar o pedir otra pagina, no mediante
un temporizador ni actualizaciones push. Permanecer en una pantalla despues de
medianoche sin interactuar no provoca una recarga automatica.
El orden y total dependen de la API; no se introduce paginacion por cursor.

La implementacion real de voz sigue pendiente, igual que los otros hallazgos
de perfil, usuarios y subidas. El siguiente bloque recomendado es extraer
notificaciones: repositorio propio, ViewModel y sincronizacion del contador
sin mantener todo el buzon en `AppViewModel`.
