# Fase 1: base de verificacion

Esta fase establece la referencia para remodelar Android por funciones sin
perder comportamientos existentes. Su alcance es compilacion debug, pruebas
locales y recorridos manuales documentados. La extraccion de SessionManager,
repositorios y ViewModels corresponde a las siguientes fases.

Esta base es la referencia inicial. La extraccion de sesion ya esta descrita
en [Sesion y autenticacion separadas](FASE_2_SESION_Y_AUTENTICACION.md).

## Verificacion automatica

Desde la raiz del proyecto:

```bash
./gradlew :app:verificarBase
```

Compila el APK debug y ejecuta las pruebas unitarias. No ejecuta
IntegracionApiTest, incluso si hay credenciales RG_API_* en el entorno.
Necesita las dependencias de Gradle y el SDK; una vez descargadas se puede
agregar `--offline`. Esto no verifica release, lint ni la interfaz en un
dispositivo.

Resultados generados:

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Informe: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Resultados XML: `app/build/test-results/testDebugUnitTest/`.

Si el sistema no tiene un JDK adecuado y Android Studio esta instalado en
macOS, se puede seleccionar su runtime para este comando:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:verificarBase
```

El README describe las limitaciones conocidas de JDK 25. No modificar el
toolchain ni saltar verificaciones permanentemente para ocultar un fallo.

### Cobertura local de referencia

| Archivo | Responsabilidad comprobada |
| --- | --- |
| ModelosTest | Fechas de CDMX, rangos, conversiones y presentacion de datos |
| ClienteApiTest | Rutas, Bearer, errores, serializacion, idempotencia y version |
| SesionTest | Limite exacto de vencimiento con reloj determinista |

ClienteApiTest tambien caracteriza tres garantias que debemos conservar:
un 401 de una peticion anterior no borra una sesion nueva, los adjuntos de
otro host no reciben el Bearer y un JSON exitoso incompatible se traduce en
un error comprensible. MockWebServer recibe las peticiones en localhost;
estas pruebas no dependen del servicio AWS.

Las pruebas de transporte no demuestran limpieza de datos entre cuentas,
correccion del estado de los ViewModels ni accesibilidad. Esas comprobaciones
se agregaran junto con los cambios de cada fase.

### Integracion remota separada

Para ejecutar expresamente el recorrido que modifica datos en la API:

```bash
./gradlew :app:testDebugUnitTest -PintegracionApi=true \
  --tests '*IntegracionApiTest' --rerun-tasks
```

Requiere RG_API_CORREO y RG_API_SECRETO previamente definidos para una cuenta
admin de pruebas. RG_API_URL permite seleccionar otro entorno; sin esa
variable se usa el Gateway de pruebas descrito en README. Nunca guardar
secretos en el repositorio. La tarea falla si faltan credenciales.

El test crea publicaciones y cuentas QA; al finalizar intenta borrar las
publicaciones y deshabilitar las cuentas. Una interrupcion puede dejar datos
que deban limpiarse. `verificarBase` rechaza `-PintegracionApi=true` antes de
ejecutar sus dependencias.

## Recorridos manuales por rol

Usar datos ficticios y un entorno de pruebas. Ejecutar cada recorrido con
la version anterior y la remodelada en el mismo dispositivo y entorno.
Registrar diferencias, no asumir que compilar equivale a funcionar.

| ID | Rol | Recorrido | Resultado esperado |
| --- | --- | --- | --- |
| R01 | Residente | Login con password y con PIN | Muro y destinos Muro, Hoy, Avisos y Perfil |
| R02 | Residente | Registro y login antes de aprobacion | Cuenta pendiente; no accede al muro |
| R03 | Residente | Filtrar muro y cargar mas | Filtro coherente, sin publicaciones duplicadas |
| R04 | Residente | Abrir aviso sin leer y volver | Contenido correcto; lectura y contador actualizados |
| R05 | Residente | Agregar, cambiar y quitar reaccion | Conteos y seleccion coherentes con la respuesta |
| R06 | Residente | Hoy, manana y semana; abrir actividad | Fechas de CDMX y detalle de la actividad elegida |
| R07 | Residente | Leer notificacion, leer todas y archivar leidas | Filas y contador coherentes; no pierde sin leer |
| R08 | Todos | Abrir imagen y descargar PDF | Imagen privada; PDF abre o informa falta de visor |
| R09 | Todos | Cambiar nombre y credencial | Datos actualizados; errores de validacion visibles |
| R10 | Residente | Cambiar preferencias con red estable | Valores confirmados por la API |
| P01 | Personal | Crear aviso, menu y evento | Campos por tipo y confirmacion de publicacion |
| P02 | Personal | Editar, archivar y republicar una publicacion propia | Cambios visibles al regresar al muro |
| P03 | Personal | Revisar permisos | Sin gestion de cuentas, fijado ni eliminacion |
| A01 | Admin | Crear, editar y adjuntar imagen o PDF | Datos y adjunto correctos; sin duplicar al reintentar |
| A02 | Admin | Fijar, archivar y deshacer | Se recupera el estado anterior indicado |
| A03 | Admin | Confirmar eliminacion | Desaparece del listado; cancelar conserva el aviso |
| A04 | Admin | Buscar, filtrar y editar usuarios | Coinciden usuario y ficha seleccionados |
| A05 | Admin | Aprobar pendiente con perfil completo | Cuenta activa y residente puede entrar |
| A06 | Admin | Alta y reposicion de credencial | Cuenta utilizable; sesiones anteriores revocadas |
| S01 | Todos | Logout y volver atras | Acceso; no vuelve a una pantalla autenticada |
| S02 | Todos | Reiniciar con sesion vigente y vencida | Restaura vigente; solicita acceso si vencio |

### Condiciones adicionales

- Probar filtros y navegacion rapida con respuestas lentas.
- Abrir y cerrar formularios durante una subida.
- Interrumpir conexion antes de una carga y durante un guardado.
- Cerrar sesion durante una solicitud y entrar con otra cuenta.
- Rotar el dispositivo y recrear el proceso durante una edicion.
- Probar letra grande de la app y del sistema, teclado abierto y TalkBack.
- Abrir detalle desde muro, agenda, buzon y archivo y comprobar el regreso.

## Fallos conocidos que no deben convertirse en comportamiento esperado

Estos hallazgos proceden de la revision estatica. Los casos manuales sirven
para confirmar su reproduccion y comprobar su correccion en la fase indicada.

| ID | Caso que debe corregirse | Responsable actual | Fase sugerida |
| --- | --- | --- | --- |
| F01 | Corregido en los modulos extraidos: cambio de sesion cancela tareas y descarta respuestas tardias; pruebas locales | core/session y ViewModels por funcionalidad | Sesion |
| F02 | Corregido: invalidacion ejecuta limpieza privada; pruebas locales de sesion y descarga | ClienteApi, GestorSesion y media/data/RepositorioMediosApi | Sesion |
| F03 | Corregido: detalle A no sobrescribe B; pruebas de respuesta y reaccion tardias | publications/detail/DetalleViewModel | Publicaciones |
| F04 | Corregido: guardado serial conserva ultima eleccion y recupera confirmado; pruebas de concurrencia | profile/PerfilViewModel.cambiarPref | Perfil |
| F05 | Corregido en fase 7: confirmar fallido conserva pendiente; guardar sin confirmar no activa | users/UsuariosViewModel.aprobar + prueba de regresion | Usuarios |
| F06 | Escuchar cambia el boton sin reproducir audio | DetalleViewModel.alternarVoz y DetalleScreen | Accesibilidad |
| F07 | Corregido: carga, error con reintento y vacio diferenciados; prueba local | publications/feed/MuroViewModel y MuroScreen | Publicaciones |
| F08 | Corregido en fase 8: upload tardio no cambia otro formulario ni usa token nuevo para limpiar; pruebas locales | publications/editor/EditorPublicacionViewModel.adjuntar | Publicacion administrativa |

No escribir pruebas que exijan conservar estos fallos. Al corregir cada uno,
agregar una prueba del resultado deseado y actualizar su estado con evidencia.

## Mapa para localizar responsabilidades actuales

| Sintoma | Punto de entrada |
| --- | --- |
| Error de red o respuesta ilegible | data/red/ClienteApi.kt y ErrorApi.kt |
| Contrato o ruta incorrecta | data/red/ApiServicio.kt y Dtos.kt |
| Sesion o usuario persistido | core/session/AlmacenSesionPreferencias.kt |
| Restauracion o carga inicial bloqueada | app/ArranqueViewModel.kt |
| Login, registro, cuenta pendiente o errores del formulario | auth/AccesoViewModel.kt y auth/AccesoScreen.kt |
| Contrato HTTP y confirmacion de sesion | auth/data/RepositorioAutenticacion.kt |
| Muro, filtros o paginas incoherentes | publications/feed/MuroViewModel.kt |
| Detalle, lectura o reaccion incoherentes | publications/detail/DetalleViewModel.kt |
| Agenda, pestanas o rangos incoherentes | publications/agenda/AgendaViewModel.kt |
| Archivo, meses o paginas incoherentes | publications/archive/ArchivoViewModel.kt |
| Buzon, lecturas o insignia incoherentes | notifications/NotificacionesViewModel.kt |
| Contrato HTTP de notificaciones | notifications/data/RepositorioNotificaciones.kt |
| Perfil, preferencias o escala incoherentes | profile/PerfilViewModel.kt y profile/PerfilScreen.kt |
| Contrato y persistencia de perfil propio | profile/data/RepositorioPerfil.kt |
| Contrato HTTP de publicaciones | publications/data/RepositorioPublicacionesApi.kt |
| Lista administrativa, archivar, fijar, borrar o deshacer | publications/administration/AdminPublicacionesViewModel.kt |
| Formulario, publicar, version o idempotencia | publications/editor/EditorPublicacionViewModel.kt y FormularioPublicacion.kt |
| Subida, limite de archivo o descarga privada | media/data/RepositorioMediosApi.kt y FuenteArchivos.kt |
| Aprobacion o edicion de cuenta | users/UsuariosViewModel.kt y users/data/RepositorioUsuarios.kt |
| Destino o regreso incorrecto | app/Navegacion.kt |
| Campos, botones o legibilidad | ui/componentes y ui/theme |

Este mapa describe el codigo actual. Se actualizara cuando cada responsabilidad
se extraiga a su paquete definitivo.

## Registro de una verificacion

### Resultado inicial del 5 de octubre de 2026

- `:app:verificarBase --offline`: correcto; APK debug generado.
- Pruebas locales: 25 ejecutadas, cero fallos, cero errores y cero omitidas.
- Integracion AWS: excluida; no se ejecuto el recorrido remoto.
- `:app:verificarBase -PintegracionApi=true --dry-run --offline`: rechazo
  esperado durante configuracion, antes de ejecutar dependencias.
- `git diff --check`: sin errores de formato.
- JDK utilizado: runtime de Android Studio. El daemon de Kotlin fallo y
  Gradle completo la compilacion mediante su alternativa; sigue pendiente
  homogeneizar el toolchain en una fase posterior.
- Recorridos manuales, release, lint y accesibilidad: pendientes.

Para cada cambio registrar en su descripcion:

```text
Revision o commit:
Entorno y dispositivo:
Comando y resultado automatico:
Recorridos manuales ejecutados por ID:
Resultado y diferencias observadas:
Fallos conocidos relacionados:
Comprobaciones pendientes:
```

La fase queda preparada cuando el comando local compila y sus pruebas pasan,
la integracion requiere habilitacion explicita y los recorridos estan
documentados. Los recorridos manuales permanecen pendientes hasta ejecutarse
en dispositivo; este documento no los presenta como aprobados.
