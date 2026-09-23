plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Serializacion: la version DEBE ser la misma que la de Kotlin (1.9.24).
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}
