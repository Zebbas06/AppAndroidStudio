# Notificaciones y contador separados

Este documento registra la extraccion del buzon. El perfil que se describe
como pendiente ya fue extraido en [Fase 6](FASE_6_PERFIL_Y_PREFERENCIAS.md).
El README contiene el mapa vigente.

## Alcance

Se extraen de AppViewModel el buzon, la insignia de no leidas y la
sincronizacion con lecturas de publicaciones. Las rutas HTTP, DTOs,
permisos del servidor, AWS y base de datos no cambian.
La division sigue siendo por paquetes dentro del modulo Android app.

## Responsabilidades

Dentro de `app/src/main/java/com/residenciaguadalupe/news/`:

```text
notifications/
  data/RepositorioNotificaciones.kt
    RepositorioNotificaciones     contrato
    RepositorioNotificacionesApi  HTTP y conversion de DTO
    PaginaNotificaciones          pagina y contador con nombres explicitos
  NotificacionesViewModel.kt      EstadoNotificaciones, buzon e insignia
  AvisosScreen.kt                 presentacion y acciones del buzon
```

La instancia del repositorio se crea en AplicacionResidencia.
Navegacion inyecta repositorio, GestorSesion y flujo de cambios de
publicaciones en un ViewModel con alcance Activity. Este alcance mantiene
la insignia activa aunque el usuario no abra el buzon.

AppViewModel ya no contiene listas, paginas, lecturas, archivado,
contador ni observers de publicaciones. Pasa de 365 a 256 lineas.
Repositorio deja de implementar notificaciones; conserva medios y usuarios.

## Comunicacion

```text
AvisosScreen -> NotificacionesViewModel -> RepositorioNotificaciones -> ApiServicio
BarraInferior <- EstadoNotificaciones.sinLeer

Detalle -> RepositorioPublicaciones -> CambioPublicacion.Leida
                                    -> NotificacionesViewModel
                                    -> marca filas del mismo postId
                                    -> consulta contador autoritativo

GestorSesion -> revision o perdida del permiso de residente
             -> cancela tareas y limpia buzon e insignia
```

El ViewModel no navega: al tocar una notificacion invoca el callback recibido
por la pantalla. Si hay publicacion asociada, se abre inmediatamente; el
marcado HTTP se confirma por separado. Leer la notificacion no marca la
publicacion: detalle conserva esa responsabilidad.

El estado es un StateFlow de solo lectura, con listas no mutables para la
pantalla. No hay referencias entre ViewModels ni dependencia de Context o
Application dentro del ViewModel nuevo.

## Consistencia y recuperacion

Las consultas de paginas, contador, lecturas, leer todas, archivado y eventos
de lectura comparten un Mutex. Una operacion espera a la anterior:
un snapshot solicitado antes de una escritura se aplica antes de ella,
no despues. Esto prioriza coherencia; una consulta lenta puede retrasar
otra operacion del mismo buzon.

- Una fila cambia a leida con la respuesta aceptada por el servidor.
  Se elimino el marcado optimista que quedaba falso tras un error.
- No se descuenta la insignia dos veces: se consulta el contador real.
  Si falla, se conserva el ultimo valor y se expone un error con reintento.
- Leer todas confirma filas y contador despues de una respuesta exitosa.
- Los controles impiden duplicar escrituras mientras hay una en curso.
- Una lectura de publicacion se procesa despues del snapshot en curso y
  actualiza todas las notificaciones cargadas que tengan ese postId.
- La paginacion elimina duplicados por ID y avanza solo despues de exito.
  Un fallo conserva filas y permite reintentar la misma pagina.
- Archivar recarga la primera pagina: continuar con el offset anterior
  podria omitir notificaciones despues de retirar filas del servidor.
- Recargar cancela la consulta anterior; su respuesta y finally no pisan
  la carga nueva.
- Cambiar cuenta, salir o perder el permiso de residente limpia todo.
  Se cancelan tambien acciones y sincronizaciones de lectura. Una generacion
  local evita que el finally de una accion vieja modifique la nueva.
- Personal y administracion no consultan ni ejecutan operaciones del buzon.

## Donde buscar fallos

| Sintoma | Archivo inicial |
| --- | --- |
| Lista, contador o accion incoherente | notifications/NotificacionesViewModel.kt |
| Error de pagina o reintento | NotificacionesViewModel.cargar |
| Lectura de detalle no sincroniza buzon | Observer de CambioPublicacion.Leida |
| Ruta, DTO o resultado incorrecto | notifications/data/RepositorioNotificaciones.kt |
| Estado visual, controles o vacio | notifications/AvisosScreen.kt |
| Insignia o alcance incorrecto | Navegacion.kt |
| Limpieza de cuenta | GestorSesion y NotificacionesViewModel.limpiar |

## Verificacion

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:verificarBase --offline -Pkotlin.compiler.execution.strategy=in-process
```

Resultado del 5 de octubre de 2026: APK debug compilado y 92 pruebas locales
aprobadas, sin fallos ni omisiones en las suites locales. Integracion AWS
excluida expresamente.

Esta fase agrega 18 pruebas:
- 14 de ViewModel: carga de insignia sin abrir buzon, permisos, paginas,
  reintentos, lectura fallida y confirmada, doble envio, leer todas, archivado,
  lectura de publicacion durante snapshot, error de contador, orden de
  snapshots, fallo masivo, perdida de permiso y cierre con respuesta tardia.
  Algunos escenarios se comprueban juntos dentro del mismo caso.
- 4 de repositorio: conversion de pagina y contador, marcado confirmado,
  rutas y resultados de acciones, y propagacion de ErrorApi.

No se realizaron pruebas visuales en emulador/dispositivo ni peticiones a AWS.
Pendiente recorrer: insignia al entrar; buzon -> detalle -> regreso; lectura
sin red; leer todas; archivar tras varias paginas; cerrar y entrar con otra
cuenta; letra grande y TalkBack.

## Limites y siguiente paso

No se agregan polling, push ni cache persistente. El contador se consulta al
entrar a una sesion residente, cargar paginas, confirmar lectura o recibir
el evento de lectura de una publicacion. Cambios externos sin esas acciones
no actualizan automaticamente la insignia.
Los eventos son locales y sin replay; el servidor sigue siendo la fuente
de verdad. La revision de contenido no proporciona transacciones del backend.

El siguiente bloque recomendado es perfil y preferencias: ViewModel propio,
estado independiente y orden de escrituras para evitar que cambios rapidos
de preferencias se sobrescriban. La implementacion real de voz, los hallazgos
de usuarios y las subidas concurrentes siguen pendientes.
