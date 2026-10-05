# Fase 9: acceso y arranque

## Objetivo

Retirar AppViewModel: mezclaba restauracion, usuario para navegacion,
login y registro. La separacion no cambia endpoints ni el esquema de datos.
Cierra los bloques estructurales previstos para Android, no la validacion
integral ni los pendientes funcionales. Se mantienen paquetes dentro del
modulo app; no son modulos Gradle independientes.

## Distribucion

| Archivo | Responsabilidad |
| --- | --- |
| app/ArranqueViewModel.kt | Restaurar sesion guardada y exponer usuario/revision para el grafo |
| app/Navegacion.kt | Destinos, factories, alcance de ViewModels y transiciones de cuenta |
| auth/AccesoViewModel.kt | Coordinar login/registro y exponer estado de envio y resultados |
| auth/AccesoScreen.kt | Campos, metodo, credencial temporal y modos de formulario |
| auth/domain/ValidarAcceso.kt | Nombre/correo y diferencia entre login existente y alta nueva |
| auth/domain/ValidarCredencial.kt | Reglas compartidas de PIN y contrasena |
| auth/data/RepositorioAutenticacion.kt | Interfaz inyectable y adaptador HTTP de autenticacion |
| core/session/GestorSesion.kt | Fuente de verdad, revisiones, cancelacion y limpieza privada |
| AplicacionResidencia.kt | Construccion del adaptador con cliente y ambito de aplicacion |
| MainActivity.kt | Activity y composicion raiz con factories de arranque y perfil |

Todos los ViewModels reciben dependencias por constructor. Ya no necesitan
Application ni crean clientes HTTP. La Activity y la navegacion ensamblan
las instancias con el contenedor existente, sin agregar un framework de DI.

## Comunicacion

Inicio -> ArranqueViewModel -> auth.yo -> GestorSesion -> EstadoArranque -> grafo.
Formulario -> AccesoViewModel -> validarAcceso -> repositorio -> API.
Login confirmado -> GestorSesion.iniciar -> revision nueva -> navegar al muro.
Registro confirmado -> registroListo -> espera de recepcion; no instala sesion.
Perfil salir -> ArranqueViewModel.cerrarSesion -> auth.salir -> cierre local
inmediato y revocacion remota con token capturado.

AccesoViewModel vive por entrada de acceso. ArranqueViewModel vive en la Activity.
Ambos observan el gestor; ninguno escribe el estado del otro.

## Comportamiento y protecciones

- Sesion ausente o vencida termina la carga sin consultar auth/me.
- Sesion vigente espera validacion. Sin red conserva la cuenta guardada,
  igual que el comportamiento anterior; las operaciones protegidas siguen
  dependiendo de permisos y conectividad en el servidor.
- Un 401/403 invalida esa sesion. Si el interceptor cancela la restauracion,
  el observer igualmente desbloquea el arranque al cerrarse la cuenta.
- Un cambio de revision termina la espera incluso si la tarea antigua fue
  cancelada antes de ejecutar su cuerpo; no hay dependencia exclusiva de finally.
- Respuestas de una cuenta anterior no sustituyen la cuenta nueva.
- No se duplica el envio; los campos y enlaces se deshabilitan mientras se procesa.
- Cambiar o limpiar el formulario cancela su trabajo y descarta resultados tardios.
- EstadoAcceso no contiene contrasenas/PIN. El secreto solo vive en remember
  de la pantalla y en los parametros temporales de la llamada; no se persiste.
  Cambiar modo, confirmar registro o mostrar cuenta pendiente limpia el secreto.
- Login no impone el minimo nuevo de contrasena a cuentas existentes.
  Registro exige nombre y reglas de credenciales nuevas.
- ACCOUNT_PENDING se presenta como espera, no como acceso exitoso.
- Nombre/correo se recortan en el adaptador. El registro no modifica GestorSesion.
- Se conserva el diseno y las rutas funcionales; el grafo ahora vive en app.

## Verificacion

27 pruebas nuevas: AccesoViewModelTest (11), ArranqueViewModelTest (10),
ValidarAccesoTest (4) y dos adicionales en RepositorioAutenticacionTest.
La base suma 188 pruebas locales y compila APK debug con verificarBase.
MockWebServer verifica registro sin login y rechazo de login anterior.
No se ejecuto integracion contra AWS ni un recorrido nuevo en dispositivo.

## Cierre pendiente

La segmentacion estructural prevista esta implementada. Antes de declarar
cerrada la remodelacion hay que ejecutar y registrar:

- Recorridos de residente, empleado y admin.
- Arranque con sesion vigente/vencida, 401, cuenta deshabilitada y sin red.
- Login con contrasena/PIN, registro pendiente, aprobacion y entrada posterior.
- Logout/cambio de cuenta durante consultas, guardados y selector de archivos.
- Teclado, rotacion, letra grande, TalkBack y regreso desde cada pantalla.
- Integracion remota explicita en entorno de pruebas con datos ficticios.
- Revision de pendientes funcionales: TextToSpeech real, accesibilidad final,
  release firmado y configuracion de produccion, entre otros descritos en README.

No se certifican aqui infraestructura AWS, backend, base de datos ni seguridad
de produccion. Los documentos de fases anteriores describen avances historicos;
README y el mapa de fase 1 describen la estructura actual.
