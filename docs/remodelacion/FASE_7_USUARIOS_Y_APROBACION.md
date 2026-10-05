# Fase 7: usuarios y aprobacion

## Responsabilidades

- `users/UsuariosScreen.kt`: lista y formulario Compose, navegacion de salida y bloqueo visual durante guardados.
- `users/UsuariosViewModel.kt`: estado observable de lista/formulario, validacion, permisos y coordinacion de peticiones. Mantiene mutableStateOf; no introduce una migracion adicional a StateFlow.
- `users/data/RepositorioUsuarios.kt`: interfaz inyectable y adaptador de operaciones HTTP de cuentas.
- `AplicacionResidencia.kt`: construye el repositorio con el cliente autenticado compartido.
- `Navegacion.kt`: construye el ViewModel; las pantallas no crean clientes HTTP.
- `auth/domain/ValidarCredencial.kt`: reglas compartidas de PIN y contrasena.

Flujo: pantalla -> ViewModel -> RepositorioUsuarios -> ApiServicio -> API.
GestorSesion cancela operaciones al cambiar de cuenta. La API sigue siendo la autoridad de permisos.

## Correcciones

Confirmar calcula un estado objetivo activo sin modificar anticipadamente el formulario.
Solo la respuesta exitosa reemplaza la ficha. Si falla, sigue pendiente y Guardar sin confirmar envia pending.
El selector no permite saltar esa confirmacion en una cuenta pendiente.

Cambiar de ficha, crear otra o abandonar el editor cancela cargas/guardados y limpia secretos.
Las respuestas tardias no reemplazan la ficha actual. Perder el rol admin tambien limpia el estado.
Los campos quedan deshabilitados durante el guardado y se evita el doble envio.
Una busqueda distinta reinicia la paginacion. Una escritura invalida y cancela consultas de lista anteriores.

Cancelar una peticion en Android no revierte una escritura que el servidor ya haya recibido;
al volver al listado se consulta nuevamente para conocer el estado real.

## Verificacion y limites

Nueve pruebas nuevas del ViewModel cubren aprobacion fallida, confirmacion diferida,
perfil obligatorio, selector, respuestas tardias, limpieza, permisos, busqueda y credenciales.
La base local suma 121 pruebas. No se ejecutaron pruebas remotas ni un recorrido nuevo en dispositivo.
El contrato HTTP se conserva; falta ampliar pruebas dedicadas del adaptador de usuarios.
Los permisos locales no sustituyen las comprobaciones del backend.

## Lo que falta

Dos bloques estructurales principales: separar medios y formulario administrativo de publicaciones;
y ordenar acceso/arranque con sus pantallas y dependencias dentro de auth.
Despues: recorridos integrales por rol en dispositivo, accesibilidad y errores de red.
Lectura real con TextToSpeech y otros pendientes funcionales son trabajo adicional, no una extraccion.
El backend Node/AWS/MySQL no esta en este repositorio; su remodelacion requiere revisar su codigo por separado.
