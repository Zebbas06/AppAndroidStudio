package com.residenciaguadalupe.news.core.voz

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Lee textos en voz alta. Una sola lectura a la vez: empezar otra corta la anterior. */
interface LectorVoz {
    /**
     * [alTerminar] recibe null al acabar la lectura, o un mensaje si el telefono
     * no puede leer. No se llama si antes se pidio [detener].
     */
    fun leer(texto: String, alTerminar: (String?) -> Unit)
    fun detener()
}

/**
 * TextToSpeech del sistema en espanol de Mexico, un poco mas lento que el
 * normal para que se entienda bien. Vive mientras la aplicacion: el motor
 * tarda en arrancar y no conviene crearlo en cada aviso.
 * Se usa solo desde el hilo principal.
 */
class LectorVozAndroid(contexto: Context) : LectorVoz {
    private val principal = Handler(Looper.getMainLooper())
    /** null mientras el motor arranca. */
    private var listo: Boolean? = null
    private var pendiente: Pair<String, (String?) -> Unit>? = null
    private var turno = 0
    private var lecturaActual: String? = null
    private var ultimoFragmento: String? = null
    private var alTerminarActual: ((String?) -> Unit)? = null

    private val tts = TextToSpeech(contexto.applicationContext) { estado -> principal.post { iniciado(estado) } }

    private val oyente = object : UtteranceProgressListener() {
        override fun onStart(id: String?) = Unit
        override fun onDone(id: String?) {
            principal.post { if (id != null && id == ultimoFragmento) terminar(null) }
        }
        @Deprecated("Requerido por la API", ReplaceWith(""))
        override fun onError(id: String?) {
            principal.post { if (id != null && id.substringBefore('#') == lecturaActual) terminar(NO_DISPONIBLE) }
        }
    }

    private fun iniciado(estado: Int) {
        listo = estado == TextToSpeech.SUCCESS && configurarIdioma()
        if (listo == true) {
            tts.setSpeechRate(0.9f)
            tts.setOnUtteranceProgressListener(oyente)
        }
        pendiente?.let { (texto, alTerminar) ->
            pendiente = null
            leer(texto, alTerminar)
        }
    }

    private fun configurarIdioma(): Boolean =
        tts.setLanguage(Locale("es", "MX")) >= TextToSpeech.LANG_AVAILABLE ||
            tts.setLanguage(Locale("es")) >= TextToSpeech.LANG_AVAILABLE

    override fun leer(texto: String, alTerminar: (String?) -> Unit) {
        when (listo) {
            null -> { pendiente = texto to alTerminar; return }
            false -> { alTerminar(NO_DISPONIBLE); return }
            true -> Unit
        }
        val lectura = "lectura${++turno}"
        val partes = fragmentos(texto, TextToSpeech.getMaxSpeechInputLength() - 1)
        if (partes.isEmpty()) { alTerminar(null); return }
        lecturaActual = lectura
        ultimoFragmento = "$lectura#${partes.lastIndex}"
        alTerminarActual = alTerminar
        partes.forEachIndexed { i, parte ->
            val cola = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (tts.speak(parte, cola, null, "$lectura#$i") == TextToSpeech.ERROR) {
                tts.stop()
                terminar(NO_DISPONIBLE)
                return
            }
        }
    }

    override fun detener() {
        pendiente = null
        lecturaActual = null
        ultimoFragmento = null
        alTerminarActual = null
        if (listo == true) tts.stop()
    }

    private fun terminar(error: String?) {
        val alTerminar = alTerminarActual
        lecturaActual = null
        ultimoFragmento = null
        alTerminarActual = null
        alTerminar?.invoke(error)
    }

    private companion object {
        const val NO_DISPONIBLE = "La lectura en voz alta no está disponible en este teléfono."

        /** El motor rechaza textos mas largos que su limite: se corta en espacios. */
        fun fragmentos(texto: String, maximo: Int): List<String> {
            val partes = mutableListOf<String>()
            var resto = texto.trim()
            while (resto.length > maximo) {
                val corte = resto.lastIndexOf(' ', maximo).takeIf { it > 0 } ?: maximo
                partes += resto.substring(0, corte)
                resto = resto.substring(corte).trimStart()
            }
            if (resto.isNotEmpty()) partes += resto
            return partes
        }
    }
}
