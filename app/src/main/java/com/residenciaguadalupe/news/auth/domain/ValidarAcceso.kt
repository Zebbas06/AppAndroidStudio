package com.residenciaguadalupe.news.auth.domain

/** Login acepta credenciales existentes; el registro exige las reglas de nuevas. */
fun validarAcceso(nombre: String?, correo: String, metodo: String, secreto: String, registro: Boolean): String? {
    if (registro && (nombre == null || nombre.trim().length !in 2..150)) return "Escriba su nombre completo."
    if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(correo.trim())) return "Revise su correo electrónico."
    return validarCredencial(metodo, secreto, nueva = registro)
}
