# Regras do R8 para o build de release.
# Room, Compose, Coroutines e Lifecycle já trazem suas próprias regras (consumer rules),
# e classes declaradas no AndroidManifest (Activity, AccessibilityService, Application)
# são mantidas automaticamente.

# Mantém nomes das entidades/enums usados como TEXT no banco (CategoriaDespesa.valueOf).
-keepclassmembers enum com.motoristapro.data.local.entity.** { *; }

# Firebase/Firestore usam reflexão nos modelos; as regras vêm com as bibliotecas,
# mas mantemos as entidades do banco por segurança na leitura do JSON.
-keep class com.motoristapro.data.local.entity.** { *; }

# Stack traces legíveis no Play Console (envie o mapping.txt gerado junto do release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
