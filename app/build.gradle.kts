plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** Base de Retrofit: con barra final y sin repetir api/v1 en las rutas. */
val URL_API_PRUEBAS = "https://jmuq4nqote.execute-api.us-east-1.amazonaws.com/api/v1/"

android {
    namespace = "com.residenciaguadalupe.news"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.residenciaguadalupe.news"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // API Gateway de pruebas (HTTPS). No usar el host de Elastic Beanstalk:
            // la documentacion de la API exige entrar siempre por el Gateway.
            buildConfigField("String", "URL_API", "\"$URL_API_PRUEBAS\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Mismo Gateway que debug mientras no exista un dominio de produccion.
            // Release no acepta HTTP: si esta URL no es https la app no conectara.
            buildConfigField("String", "URL_API", "\"$URL_API_PRUEBAS\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (zona America/Mexico_City) en minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // Debe corresponder a la version de Kotlin (1.9.24 -> 1.5.14)
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Tipografias de Google Fonts descargables (Playfair Display y Source Sans 3)
    implementation("androidx.compose.ui:ui-text-google-fonts")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // --- Cliente HTTP -----------------------------------------------------
    // OkHttp 4.12 y Retrofit 2.11 son compatibles con Kotlin 1.9.24 y minSdk 24.
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    // Solo en depuracion: nunca en release y nunca a nivel BODY en autenticacion.
    debugImplementation("com.squareup.okhttp3:logging-interceptor")

    // Imagenes y PDF privados: Coil comparte el OkHttp autenticado y se
    // configura sin cache en disco (Cache-Control: private, no-store).
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Rotacion EXIF al convertir fotos de camara o galeria a JPG.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Pruebas ----------------------------------------------------------
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// La integracion escribe en AWS: debe habilitarse expresamente.
val integracionApi = providers.gradleProperty("integracionApi").map { it == "true" }.orElse(false)
tasks.withType<Test>().configureEach {
    if (!integracionApi.get()) {
        exclude("**/IntegracionApiTest*")
    } else {
        doFirst {
            check(!System.getenv("RG_API_CORREO").isNullOrBlank() &&
                !System.getenv("RG_API_SECRETO").isNullOrBlank()) {
                "La integracion requiere RG_API_CORREO y RG_API_SECRETO de una cuenta de pruebas."
            }
        }
    }
}

tasks.register("verificarBase") {
    check(!integracionApi.get()) {
        "verificarBase es local. Ejecute la integracion por separado."
    }
    group = "verification"
    description = "Compila debug y verifica las pruebas locales de referencia, sin llamadas a AWS."
    dependsOn("assembleDebug", "testDebugUnitTest")
}
