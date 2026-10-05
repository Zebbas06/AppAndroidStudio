package com.residenciaguadalupe.news.core.session

import kotlinx.coroutines.CancellationException

/** Un cierre de sesion debe cancelar la operacion, no convertirse en un error de UI. */
suspend fun <T> intentarOperacion(bloque: suspend () -> T): Result<T> = try {
    Result.success(bloque())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
