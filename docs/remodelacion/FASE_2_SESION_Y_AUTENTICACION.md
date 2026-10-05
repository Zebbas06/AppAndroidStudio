# Sesion y autenticacion separadas

Primera extraccion de codigo de produccion de la remodelacion. La sesion
tiene un responsable propio y la autenticacion deja de vivir dentro del
repositorio general. Se conserva un solo modulo Gradle, app.

## Donde buscar

```text
core/session/
  Sesion.kt                        Token, usuario, vencimiento y revision
  AlmacenSesion.kt                  Contrato de persistencia
  AlmacenSesionPreferencias.kt      Persistencia Android
  GestorSesion.kt                   Transiciones, cancelacion y limpieza
  IntentarOperacion.kt              Propagacion de cancelacion
auth/data/
  RepositorioAutenticacion.kt       Login, registro, logout y perfil propio
data/red/
  ClienteApi.kt                     Bearer, expiracion y aviso de 401
  InterceptorImagenSesion.kt        Cache de imagenes aislada por sesion
```

| Problema | Responsable |
| --- | --- |
| Token o usuario guardado incorrectamente | AlmacenSesionPreferencias |
| Cierre, cambio de cuenta o tareas anteriores | GestorSesion |
| Login, registro o perfil propio | RepositorioAutenticacion |
| Cabecera Bearer o respuesta 401 | ClienteApi |
| Imagen que termina despues del cambio de cuenta | InterceptorImagenSesion |
| Recursos concretos que deben limpiarse | AplicacionResidencia |
| Campos o listas que no se reinician | limpiarEstado del ViewModel correspondiente |

## Responsabilidades

AlmacenSesion solo lee, guarda y borra datos. Mantiene el archivo
sesion_residencia y sus claves originales, de forma que no se pierden sesiones
existentes por el cambio de paquetes. Las reglas de respaldo siguen vigentes.

GestorSesion publica EstadoSesion mediante StateFlow. Cada entrada o cierre
incrementa una revision; actualizar el nombre o perfil conserva esa revision.
Esto permite reconocer un cambio de cuenta aunque el observador no alcance
a recibir el estado intermedio sin sesion.

El gestor cancela las tareas registradas mediante lanzar(viewModelScope),
ejecuta la limpieza recibida por constructor y controla que un token antiguo
no actualice el usuario de una sesion nueva. No importa clases de pantallas,
OkHttp ni Coil. Un cerrojo coordina las transiciones que pueden llegar desde
el hilo de red y desde la interfaz.

RepositorioAutenticacion recibe ApiServicio, GestorSesion y un ambito de
aplicacion por constructor. El repositorio general conserva publicaciones,
medios y administracion; ya no implementa login, registro, logout, nombre,
credencial ni preferencias propias.

AplicacionResidencia sigue como contenedor manual: construye las dependencias
y proporciona la limpieza concreta de peticiones, PDFs y memoria de imagenes.
Los ViewModels todavia son AndroidViewModel; su division por funcion y las
factorias por constructor quedan para la siguiente extraccion.

## Inicio y cierre

1. Login carga el contrato y valida que expiresAt pueda interpretarse.
2. GestorSesion verifica que no haya cambiado la revision durante la llamada.
3. Cancela trabajos anteriores, limpia recursos y guarda la nueva sesion.
4. Los ViewModels observan la revision, vacian estados anteriores y la
   navegacion reinicia la pila para la cuenta actual.

Para salir, primero se captura la sesion anterior y se cierra localmente.
Despues se intenta POST auth/logout desde el ambito de aplicacion con su
Bearer explicito. No se espera a la red para mostrar acceso. Si la revocacion
remota falla o el proceso termina, el token del servidor puede seguir vigente
hasta que expire: el cierre remoto es un intento, no una cola persistente.

Un 401 autenticado informa al gestor y utiliza la misma limpieza. Un 401 de
auth/login sigue siendo un error de credenciales. Un 401 con token anterior
no cierra una cuenta nueva. Antes de enviar el token actual se comprueba su
vencimiento; no se agrego un temporizador de expiracion mientras la app esta
inactiva.

ClienteApi compara esquema, host y puerto antes de agregar el Bearer. La
revocacion acepta un token explicito exclusivamente en la ruta de logout.

## Respuestas y recursos tardios

Las operaciones de los tres ViewModels quedan registradas en el gestor. Sus
observadores permanentes siguen en viewModelScope para poder recibir la
siguiente sesion. La revision reinicia listas, formularios, errores, indicadores
de carga, paginacion, deshacer y secretos temporales.

IntentarOperacion vuelve a lanzar CancellationException en lugar de tratarla
como un fallo recuperable. Las llamadas HTTP comprueban la cancelacion antes
y despues de recibir una respuesta. Si la respuesta cancelada contiene un
stream, se cierra.

Los PDFs se descargan a un temporal con comprobaciones de cancelacion. Antes
de publicar el archivo definitivo se comprueba el token bajo el mismo cerrojo
utilizado para el cierre. El temporal se elimina al terminar o cancelar.

Las imagenes incluyen la revision en la clave de cache. Una respuesta de
imagen de otra revision se descarta y limpia memoria; no se habilita cache
de disco. El cierre tambien cancela solicitudes HTTP en curso.

## Verificacion

```bash
./gradlew :app:verificarBase --offline \
  -Pkotlin.compiler.execution.strategy=in-process
```

El parametro del compilador evita el daemon Kotlin problematico del entorno
actual sin modificar el toolchain del repositorio.

Resultado del 5 de octubre de 2026: APK debug compilado y 38 pruebas locales
aprobadas, sin errores ni omitidas. No se ejecuto integracion remota.

- GestorSesionTest: restauracion, limpieza, invalidacion, perfil tardio,
  revision, login antiguo, cancelacion y publicacion de archivos.
- RepositorioAutenticacionTest: contrato de login, vencimiento ilegible y
  cierre inmediato con revocacion del token capturado tras otro login.
- ClienteApiTest: incluye limpieza ante 401 y conserva las garantias previas.

Pendiente en dispositivo: comprobar los campos de los tres ViewModels al
cambiar de cuenta, navegacion restaurada y cancelacion real de imagenes y
PDFs. Las pruebas locales cubren el coordinador y el transporte; no sustituyen
esas comprobaciones de interfaz.

Los fallos de detalle A/B dentro de la misma sesion, preferencias concurrentes,
aprobacion y audio siguen pendientes en sus respectivas funciones. Esta
extraccion atiende el cambio de sesion; no presenta esos fallos como resueltos.
