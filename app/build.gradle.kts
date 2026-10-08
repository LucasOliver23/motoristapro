plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")   // compilador do Compose (Kotlin 2.x)
    id("com.google.devtools.ksp")               // processador do Room (substitui o kapt)
}

// Login e nuvem só entram quando o app/google-services.json (Firebase) estiver presente.
// Sem ele, o app compila e roda normalmente, só com os dados locais.
val firebaseConfigurado = project.file("google-services.json").exists()
if (firebaseConfigurado) {
    apply(plugin = "com.google.gms.google-services")
}

// Assinatura de publicação: o GitHub Actions grava o .jks e passa as senhas por variável
// de ambiente. No seu PC essas variáveis não existem, então continua valendo a assinatura
// de depuração do Android Studio — nada muda para quem compila em casa.
val arquivoChave = System.getenv("MOTORISTAPRO_KEYSTORE")
val temChavePropria = !arquivoChave.isNullOrBlank() && file(arquivoChave).exists()

android {
    namespace = "com.motoristapro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.motoristapro"
        minSdk = 26                              // java.time nativo, sem desugaring
        targetSdk = 35
        versionCode = 50
        versionName = "3.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (temChavePropria) {
            create("publicacao") {
                storeFile = file(arquivoChave!!)
                storePassword = System.getenv("MOTORISTAPRO_KEYSTORE_SENHA")
                keyAlias = System.getenv("MOTORISTAPRO_KEY_ALIAS")
                keyPassword = System.getenv("MOTORISTAPRO_KEY_SENHA")
            }
        }
    }

    buildTypes {
        debug {
            // Sem applicationIdSuffix: o nome do pacote é sempre com.motoristapro,
            // o mesmo cadastrado no Firebase (um app só, um SHA-1 só).
            versionNameSuffix = "-debug"
        }
        release {
            // R8 desligado de propósito: o APK fica maior, mas nenhuma classe some por
            // engano (Room, Firebase, ML Kit). Como a build roda sozinha no GitHub,
            // um erro de ofuscação só apareceria no celular do motorista.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (temChavePropria) {
                signingConfigs.getByName("publicacao")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true      // expõe VERSION_CODE/VERSION_NAME para o app se autoatualizar
    }

    packaging {
        resources {
            // Evita o erro "More than one file was found with OS independent path 'META-INF/...'"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room: exporta o JSON do schema de cada versão (base para migrations) e gera código Kotlin.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ---------- Versões ----------
    val roomVersion = "2.6.1"
    val lifecycleVersion = "2.8.7"
    val coroutinesVersion = "1.8.1"
    val composeBom = "2024.12.01"

    // ---------- AndroidX base ----------
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ---------- Coroutines ----------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // ---------- Lifecycle / ViewModel ----------
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    // ---------- Firebase: login (email/senha e Google) + backup na nuvem ----------
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Funcoes do servidor: cria o link de pagamento e reconfere a assinatura
    implementation("com.google.firebase:firebase-functions")

    // Seletor de contas do Google (API atual, substitui o GoogleSignIn antigo)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Seletor clássico (GoogleSignIn): reserva para aparelhos onde o Credential Manager trava
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ---------- ML Kit: leitura de texto pela imagem (OCR), roda offline no aparelho ----------
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // ---------- WorkManager (resumo diário às 22h) ----------
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ---------- Room (SQLite) ----------
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")          // suspend + Flow
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---------- Jetpack Compose (o BOM alinha as versões) ----------
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ---------- Testes ----------
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBom"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
