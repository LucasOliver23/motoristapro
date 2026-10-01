package com.motoristapro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

// Paleta: grafite azulado em três camadas + lima como cor do lucro.
//
// O problema da versão antiga era que fundo e cartão eram dois pretos quase
// iguais — os cartões sumiam. Aqui cada nível é visivelmente diferente, e
// cada cor tem um significado fixo: lima = lucro, âmbar = atenção,
// vermelho = prejuízo, turquesa = informação. Nada de cor decorativa.
val Lima = Color(0xFF9EE64B)
val LimaEscuro = Color(0xFF6FA82F)
val VerdeLucro = Lima
val VermelhoPrejuizo = Color(0xFFF2777C)
val AmareloAlerta = Color(0xFFF6B93B)
val AzulInfo = Color(0xFF7AA7F0)
val Turquesa = Color(0xFF2DD4BF)
val Roxo = Color(0xFFC89BF0)

val Fundo = Color(0xFF0B0F14)
val Superficie = Color(0xFF141A21)
val SuperficieAlta = Color(0xFF1C242D)
val SuperficieMaisAlta = Color(0xFF253040)
val TextoPrincipal = Color(0xFFECF2F6)
val TextoSecundario = Color(0xFF8A97A6)
val Contorno = Color(0xFF1E2630)

/** Uma cor por aplicativo, para gráficos e listas (as cores da marca de cada um). */
val CorUber = Color(0xFFECF2F6)
val Cor99 = Color(0xFFF2C230)
val CorIfood = Color(0xFFE8442F)
val CorInDrive = Color(0xFF4CC26A)

/** Cor da marca do app, para o gráfico de "por aplicativo". */
fun corDaPlataforma(nome: String?): Color = when (nome?.lowercase()?.trim()) {
    "uber" -> CorUber
    "99", "99pop" -> Cor99
    "ifood" -> CorIfood
    "indrive", "indriver" -> CorInDrive
    else -> TextoSecundario
}

private val Esquema = darkColorScheme(
    primary = Lima,
    onPrimary = Color(0xFF0B0F14),
    primaryContainer = Color(0xFF16241A),
    onPrimaryContainer = Lima,
    secondary = Turquesa,
    onSecondary = Color(0xFF0B0F14),
    secondaryContainer = Color(0xFF11201C),
    onSecondaryContainer = Color(0xFFB6E8DF),
    tertiary = AmareloAlerta,
    onTertiary = Color(0xFF0B0F14),
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
    onError = Color(0xFF0B0F14)
)

/**
 * Tipografia: a mesma família do sistema, mas com os ALGARISMOS DE LARGURA FIXA
 * ("tnum") nos estilos usados para dinheiro.
 *
 * Sem isso, o 1 é mais estreito que o 8 e o valor "pula" de lado toda vez que o
 * lucro do dia atualiza — some com a sensação de painel e cansa a vista no trânsito.
 */
private val Numeros = TextStyle(fontFeatureSettings = "tnum")

private val Tipografia = Typography().run {
    copy(
        displayLarge = displayLarge.merge(Numeros),
        displayMedium = displayMedium.merge(Numeros),
        displaySmall = displaySmall.merge(Numeros),
        headlineLarge = headlineLarge.merge(Numeros),
        headlineMedium = headlineMedium.merge(Numeros),
        headlineSmall = headlineSmall.merge(Numeros),
        titleLarge = titleLarge.merge(Numeros),
        bodyLarge = bodyLarge.merge(Numeros)
    )
}

/** Tema único escuro (estilo painel automotivo), independente do tema do sistema. */
@Composable
fun MotoristaTema(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, typography = Tipografia, content = content)
}
