# Fase 8: editor de publicaciones y medios

## Objetivo

Separar responsabilidades del antiguo AdminViewModel y retirar el repositorio
general que ya solo contenia archivos. Son paquetes por funcionalidad dentro
del modulo Gradle app; no se agregan modulos de compilacion ni servicios remotos.

## Distribucion

| Carpeta o archivo | Responsabilidad |
| --- | --- |
| publications/administration/AdminPublicacionesViewModel.kt | Lista paginada, permisos de acciones, fijar, archivar, eliminar y Deshacer |
| publications/administration/AdminListaScreen.kt | Presentacion del listado y confirmacion de borrado |
| publications/editor/EditorPublicacionViewModel.kt | Carga fresca, estado del formulario, subida, version, idempotencia y publicacion |
| publications/editor/FormularioPublicacion.kt | Snapshot y validacion de aviso, menu o evento; cuerpo de escritura sin Android |
| publications/editor/AdminCrearScreen.kt | Campos, selector del sistema, carga/error y confirmacion de publicacion |
| media/data/RepositorioMedios.kt | Contrato inyectable para subir, eliminar uploads y descargar |
| media/data/RepositorioMediosApi.kt | Multipart, HTTP y cache privada de descargas |
| media/data/FuenteArchivos.kt | Metadatos y apertura de URI mediante ContentResolver |
| media/data/LecturaLimitada.kt | Lectura cancelable con limite real, independientemente del SIZE informado |
| data/repositorio/Pagina.kt | Pagina compartida; ya no vive dentro de un repositorio general |
| AplicacionResidencia.kt | Construccion y limpieza de uploads con token de origen y ambito de aplicacion |
| Navegacion.kt | Factories y ViewModels por entrada de lista/editor; ID opcional en ruta |

## Comunicacion

Lista -> callback con ID -> navegacion -> editor -> GET de version fresca.
Editor -> FormularioPublicacion -> RepositorioPublicaciones -> API.
Selector -> referencia URI -> editor -> RepositorioMedios -> FuenteArchivosAndroid -> multipart -> API.
Detalle -> RepositorioMedios -> descarga temporal -> comprobacion de sesion -> archivo privado.

Lista y editor ya no se escriben estado entre si. Al regresar al listado se
consulta nuevamente. Las escrituras conservan la invalidacion del repositorio
de publicaciones usada por muro, agenda y archivo.

## Protecciones

- Una instancia de editor por entrada evita compartir borradores entre pantallas.
- Cerrar, cambiar de cuenta o perder el rol cancela trabajos y descarta respuestas tardias.
- El token de origen del upload se conserva: no se elimina con credenciales de otra cuenta.
- Un archivo nuevo nunca enviado a publicar se limpia al quitarlo o abandonar el editor.
  La limpieza se ejecuta en el ambito de aplicacion, no en un ViewModel destruido.
- Si un adjunto participo en un envio, no se borra automaticamente: sin respuesta
  no sabemos si el servidor lo asocio. Los uploads huerfanos quedan sujetos al
  mantenimiento del backend descrito en el contrato; no se certifico ese mantenimiento aqui.
- Los controles se bloquean durante carga/subida/envio y no se duplica Publicar.
- Reintentar el mismo cuerpo conserva Idempotency-Key; un cuerpo distinto recibe otra.
- Edicion conserva If-Match y manejo de conflicto. El empleado mantiene isPinned original.
- Listado y acciones no admiten respuestas tardias que reviertan escrituras confirmadas.
  Tras una escritura, la siguiente paginacion reinicia offsets desde pagina uno.
- Deshacer se serializa con las acciones. Si republicar funciona pero volver a fijar
  falla, la lista refleja la republicacion confirmada y muestra el error del segundo paso.
- La lectura de archivos desconocidos o con SIZE incorrecto consume como maximo limite + 1 bytes.
  Se mantienen limites de 5 MB para JPG/PNG y 9 MB para PDF, y multipart de un solo envio.
- PDFs siguen usando temporal, cierre de stream, comprobacion de token y limpieza.
  Un fallo de almacenamiento se traduce en error local en lugar de propagar IOException.

## Verificacion

40 pruebas nuevas: EditorPublicacionViewModelTest (14),
AdminPublicacionesViewModelTest (8), FormularioPublicacionTest (5),
RepositorioMediosApiTest (10) y LecturaLimitadaTest (3).
La base local suma 161 pruebas sin fallos y compila el APK debug mediante verificarBase.
MockWebServer comprueba el contrato sin escribir en AWS.

No se ejecuto un nuevo recorrido en dispositivo ni la integracion remota.
Se conservaron los contratos HTTP y se actualizaron las referencias de la prueba
de integracion al constructor multipart nuevo, pero eso no certifica su ejecucion.

## Recorridos pendientes

- Admin: crear aviso, menu y evento con foto/PDF, editar y cancelar.
- Empleado: editar su publicacion fijada sin cambiar isPinned.
- Conflicto 409: conservar texto, cargar version fresca y volver a guardar.
- Salir durante subida o envio; verificar listado real al regresar.
- Fijar, archivar, Deshacer y eliminar con confirmacion.
- Rotar el dispositivo y abrir selector; comprobar que no se pierde el formulario
  mientras siga viva la entrada. Los borradores no se persisten tras muerte del proceso.
- Archivos sin permiso, sin SIZE, grandes y descarga sin espacio.
- Teclado, letra grande, TalkBack y boton de regreso del sistema.

## Siguiente bloque

Ordenar acceso y arranque en auth. Despues queda el cierre integral por rol,
accesibilidad y condiciones de red. TextToSpeech y los otros pendientes
funcionales siguen siendo trabajo adicional. Backend y base de datos no fueron
modificados ni se presentan como remodelados por estos cambios.
