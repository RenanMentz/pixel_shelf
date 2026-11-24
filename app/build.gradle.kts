import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Chave da Steam Web API (opcional): fica no local.properties, que não vai para o Git.
// Sem ela a busca funciona, só as conquistas não são importadas.
val propriedadesLocais = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.pixelshelf"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pixelshelf"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String", "STEAM_API_KEY",
            "\"${propriedadesLocais.getProperty("steam.apiKey", "").trim()}\""
        )
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.fragment:fragment-ktx:1.8.3")      // Fragments + activityViewModels()
    implementation("androidx.viewpager2:viewpager2:1.1.0")      // abas por status
    implementation("io.coil-kt:coil:2.7.0") // carregamento das capas

    // Retrofit: busca de jogos e conquistas na Steam
    val retrofit = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofit")
    implementation("com.squareup.retrofit2:converter-gson:$retrofit")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
}
