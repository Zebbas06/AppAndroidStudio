# Perfil y preferencias separados

## Alcance y distribucion

Se extraen perfil, nombre, credenciales, preferencias de notificacion y
escala de lectura de AppViewModel. La infraestructura, esquema de base de
datos y rutas HTTP permanecen iguales. No se agregan dependencias.

Dentro de `app/src/main/java/com/residenciaguadalupe/news/`:

```text
profile/
  data/RepositorioPerfil.kt  contrato y traduccion HTTP del perfil propio
  PerfilViewModel.kt         EstadoPerfil y coordinacion del guardado
  PerfilScreen.kt            datos, formularios, preferencias y escala
auth/
  domain/ValidarCredencial.kt reglas compartidas con registro
  data/RepositorioAutenticacion.kt acceso, restauracion y logout
```

AppViewModel pasa de 256 a 143 lineas. Conserva acceso, restauracion,
usuario/roles para navegacion y cierre de sesion. Ya no es propietario
del perfil ni de avisos generales sin responsabilidad definida.

RepositorioAutenticacion deja de implementar nombre, credencial y
preferencias. Esas operaciones tienen un solo propietario: RepositorioPerfil.

## Como se comunica

```text
PerfilScreen -> PerfilViewModel -> RepositorioPerfil -> ApiServicio
                                    |
                                    +-> GestorSesion.actualizarUsuario
                                        -> nombre/metodo/preferencias persistidos
                                        -> observadores de usuario de otras pantallas

MainActivity -> crea PerfilViewModel (alcance Activity)
             -> observa EstadoPerfil.escala
             -> LocalEscala para todas las pantallas
             -> pasa la misma instancia a Navegacion

Cerrar sesion en PerfilScreen -> callback de navegacion -> AppViewModel -> auth.salir
```

El ViewModel nuevo recibe dependencias por constructor, no Application,
Context ni Retrofit. Su estado es un StateFlow de solo lectura. El nombre
y credencial son formularios visuales locales; los secretos no se guardan
en EstadoPerfil, preferencias ni almacenamiento de sesion.

La escala conserva el comportamiento existente: se mantiene entre cuentas
durante la vida de la Activity y no se envia a la API. No se agrego
persistencia en disco para conservarla tras terminar el proceso.

## Guardado de preferencias

Se distinguen dos valores:
- Confirmadas: ultimo snapshot aceptado por la API.
- Deseadas: ultima eleccion del usuario, visible inmediatamente.

Hay un solo worker de preferencias. Si cambia otro interruptor durante un
envio, la respuesta anterior confirma ese snapshot pero no reemplaza la
eleccion nueva. El worker envia a continuacion el estado deseado completo.
Cambios intermedios pueden agruparse; no hay escrituras paralelas.

Si falla un envio:
- Sin cambios posteriores: restaura el ultimo confirmado y expone un error.
- Con cambios posteriores: intenta el snapshot nuevo completo, sin restaurar
  una copia anterior que eliminaria esas elecciones.

Si el usuario vuelve al valor original mientras se guarda, el worker lo
envia despues de confirmar la primera escritura cuando sea necesario.
Un GET anterior no puede sobrescribir un cambio: se cancela y se valida
la version de la eleccion antes de aplicar el resultado.

La pantalla distingue consulta y guardado, y ofrece volver a consultar
ante un error. Los interruptores siguen admitiendo cambios mientras se
guarda, que es precisamente el caso cubierto por el worker.

## Nombre, credencial y sesion

RepositorioPerfil serializa consultas y escrituras con Mutex para que una
respuesta completa de usuario no cruce otra operacion de perfil.
Actualiza el usuario del token que inicio cada operacion; capturar el token
antes de esperar el Mutex impide reutilizar accidentalmente otra cuenta.

Nombre se recorta, valida longitud y se refleja en la sesion compartida.
Guardar dos veces no envia dos ediciones mientras la primera sigue activa.

El cambio de credencial conserva la ruta y semantica existentes: 204 confirma
la operacion, las otras sesiones se revocan y esta permanece vigente.
Se confirma localmente el nuevo authMethod y se intenta refrescar auth/me.
Si ese refresco falla sin cerrar la sesion, el metodo confirmado sigue
disponible; un 401 conserva el cierre centralizado del GestorSesion.

Registro y cambio de credencial comparten limites de PIN/password. El login
no aplica la longitud minima de una credencial nueva. Un metodo desconocido
se rechaza antes de llamar a la API.

Cambiar cuenta, salir o cambiar entre residente/personal cancela tareas y
limpia estado privado, errores y elecciones pendientes. La escala se conserva
por accesibilidad. Respuestas tardias no ejecutan callbacks del formulario
ni modifican el estado de otra cuenta.

## Donde buscar

| Sintoma | Punto de entrada |
| --- | --- |
| Interruptor vuelve atras o pierde otra eleccion | PerfilViewModel.cambiarPref |
| Error de consulta o reintento | PerfilViewModel.cargarPreferencias |
| Nombre, metodo o preferencias persistidas incorrectas | profile/data/RepositorioPerfil.kt |
| Reglas distintas entre registro y perfil | auth/domain/ValidarCredencial.kt |
| Datos, formularios o feedback visual | profile/PerfilScreen.kt |
| Escala no llega a otra pantalla | MainActivity y LocalEscala |
| Respuesta aparece en otra cuenta | GestorSesion y PerfilViewModel.limpiar |
| Acceso o restauracion | AppViewModel y RepositorioAutenticacion |

## Verificacion

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:verificarBase --offline -Pkotlin.compiler.execution.strategy=in-process
```

Resultado del 5 de octubre de 2026: APK debug compilado y 112 pruebas locales
aprobadas. Esta fase agrega 20:
- 12 de ViewModel: cambios rapidos, fallos viejos/nuevos, deshacer toggle,
  GET tardio, actualizacion de usuario, cambio de cuenta, permisos, nombre,
  credencial, error de formulario y cierre con escala conservada.
- 5 de repositorio: snapshot HTTP, persistencia, nombre, credencial confirmada
  con refresco fallido y respuesta de cuenta anterior.
- 3 de validacion: login frente a nueva credencial, limites y metodos.

No se ejecutaron integracion AWS ni pruebas visuales en dispositivo/emulador.
Pendiente validar nombre reflejado en muro; cambio password/PIN; toggles
rapidos con red lenta y sin red; nueva cuenta sin elecciones anteriores;
escala en todos los destinos y formularios con letra grande/TalkBack.

## Limites y siguiente paso

No se agrega cola persistente offline ni sincronizacion entre dispositivos.
Una peticion con resultado de red ambiguo podria haberse aplicado en el
servidor; volver a consultar reconcilia el estado. Cancelar un envio evita
publicar su respuesta local, pero no garantiza revertir una escritura
que el servidor ya recibio.

La restauracion de formularios tras terminar el proceso sigue pendiente.
La escala sigue siendo local en memoria. La implementacion real de voz,
aprobacion de usuarios y subidas de formularios distintos siguen pendientes.

Siguiente bloque recomendado: usuarios y administracion de cuentas.
Extraer su repositorio del servicio general y resolver el hallazgo de
aprobacion seguido de guardado sin confirmacion, con pruebas de su transicion
de estado. Despues, medios y formulario administrativo de publicaciones.
