package com.residenciaguadalupe.news.data.repositorio

/** Una pagina ya convertida. [hayMas] segun meta.total. */
data class Pagina<T>(val elementos: List<T>, val pagina: Int, val limite: Int, val total: Int) {
    val hayMas get() = pagina * limite < total
}
