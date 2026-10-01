// Versões fixas e compatíveis entre si (Kotlin 2.0.21 <-> KSP 2.0.21-1.0.28 <-> Compose plugin 2.0.21).
// Se atualizar o Kotlin, atualize o KSP para a versão com o MESMO prefixo.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // Kotlin 2.x: o compilador do Compose vem como plugin, com a MESMA versão do Kotlin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Firebase: aplicado no app/build.gradle.kts só quando google-services.json existir.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
