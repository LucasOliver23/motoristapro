package com.motoristapro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta: fundo quase preto esverdeado + verde-limão de destaque.
val Lima = Color(0xFFA3E635)
val LimaEscuro = Color(0xFF65A30D)
val VerdeLucro = Lima
val VermelhoPrejuizo = Color(0xFFF87171)
val AmareloAlerta = Color(0xFFFACC15)
val AzulInfo = Color(0xFF60A5FA)

val Fundo = Color(0xFF0A0D0B)
val Superficie = Color(0xFF121713)
val SuperficieAlta = Color(0xFF1A211B)
val SuperficieMaisAlta = Color(0xFF222B23)
val TextoPrincipal = Color(0xFFE8F0E9)
val TextoSecundario = Color(0xFF9FB0A2)
val Contorno = Color(0xFF2E3A30)

private val Esquema = darkColorScheme(
    primary = Lima,
    onPrimary = Color(0xFF0A0D0B),
    primaryContainer = Color(0xFF1E2A10),
    onPrimaryContainer = Lima,
    secondary = Color(0xFF86EFAC),
    onSecondary = Color(0xFF0A0D0B),
    secondaryContainer = Color(0xFF1B2A20),
    onSecondaryContainer = Color(0xFFBBF7D0),
    tertiary = AmareloAlerta,
    onTertiary = Color(0xFF0A0D0B),
    background = Fundo,
    onBackground = TextoPrincipal,
    surface = Fundo,
    onSurface = TextoPrincipal,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoSecundario,
    surfaceContainerLowest = Fundo,
    surfaceContainerLow = Superficie,
    surfaceContainer = Superficie,
    surfaceContainerHigh = SuperficieAlta,
    surfaceContainerHighest = SuperficieMaisAlta,
    outline = Contorno,
    outlineVariant = Contorno,
    error = VermelhoPrejuizo,
    onError = Color(0xFF0A0D0B)
)

/** Tema único escuro (estilo painel automotivo), independente do tema do sistema. */
@Composable
fun MotoristaTema(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, content = content)
}
