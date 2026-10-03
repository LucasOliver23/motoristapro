package com.motoristapro.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Paleta: grafite azulado em três camadas (escuro) ou papel em três camadas (claro),
 * mais as cores de significado fixo — lucro, atenção, prejuízo, informação.
 *
 * O motorista escolhe o modo em Mais > Aparência. Para o resto do app NADA muda:
 * os nomes das cores (Lima, TextoSecundario, Fundo...) continuam os mesmos, só
 * passaram a ser lidos de [paletaAtual]. Como é um `mutableStateOf`, qualquer tela
 * que use uma dessas cores se redesenha sozinha quando o modo troca.
 */
// ------------------------------------------------- a paleta, com nome próprio
//
// Definida uma vez aqui. O resto do app usa os nomes de SIGNIFICADO (Lima para
// lucro, VermelhoPrejuizo para gasto) — assim o tema claro troca a cor sem
// ninguém mexer em tela nenhuma. Estas constantes são a origem do tema escuro.

val FundoApp = Color(0xFF0D1117)
val FundoCard = Color(0xFF161C24)
val VerdeNeon = Color(0xFF39FF14)
val VermelhoDespesa = Color(0xFFFF5252)
val FundoBotaoEscuro = Color(0xFF21262D)

private class Paleta(
    val lucro: Color,
    val lucroEscuro: Color,
    val prejuizo: Color,
    val alerta: Color,
    val info: Color,
    val turquesa: Color,
    val roxo: Color,
    val fundo: Color,
    val superficie: Color,
    val superficieAlta: Color,
    val superficieMaisAlta: Color,
    val textoPrincipal: Color,
    val textoSecundario: Color,
    val contorno: Color,
    val uber: Color
)

private val ESCURA = Paleta(
    // Paleta definida pelo Oliver: preto de painel, verde neon no dinheiro.
    lucro = VerdeNeon,
    lucroEscuro = Color(0xFF2ECC0F),
    prejuizo = VermelhoDespesa,
    alerta = Color(0xFFFFC94D),
    info = Color(0xFF58A6FF),
    turquesa = Color(0xFF2DE2C5),
    roxo = Color(0xFFBC8CFF),
    fundo = FundoApp,
    superficie = FundoCard,
    superficieAlta = FundoBotaoEscuro,
    superficieMaisAlta = Color(0xFF2B323B),
    textoPrincipal = Color(0xFFFFFFFF),
    textoSecundario = Color(0xFF8B949E),
    // Contorno quase do tom do cartão: separa sem desenhar moldura.
    contorno = Color(0xFF21262D),
    uber = Color(0xFFFFFFFF)
)

// No claro as cores de significado precisam ESCURECER: lima sobre branco não se lê.
// O tom muda, o significado não — verde continua lucro, vermelho continua prejuízo.
private val CLARA = Paleta(
    lucro = Color(0xFF16A34A),
    lucroEscuro = Color(0xFF15803D),
    prejuizo = Color(0xFFDC2626),
    alerta = Color(0xFFB45309),
    info = Color(0xFF2563EB),
    turquesa = Color(0xFF0F8276),
    roxo = Color(0xFF6A32B0),
    fundo = Color(0xFFF1F5F9),
    superficie = Color(0xFFFFFFFF),
    superficieAlta = Color(0xFFFFFFFF),
    superficieMaisAlta = Color(0xFFE2E8F0),
    textoPrincipal = Color(0xFF0F172A),
    textoSecundario = Color(0xFF64748B),
    contorno = Color(0xFFE2E8F0),
    uber = Color(0xFF0F172A)
)

private val paletaAtual = mutableStateOf(ESCURA)
private val escuroAtual = mutableStateOf(true)

/** true quando o app está desenhando no modo escuro (já resolvendo "do sistema"). */
val temaEscuro: Boolean get() = escuroAtual.value

val Lima: Color get() = paletaAtual.value.lucro
val LimaEscuro: Color get() = paletaAtual.value.lucroEscuro
val VerdeLucro: Color get() = paletaAtual.value.lucro
val VermelhoPrejuizo: Color get() = paletaAtual.value.prejuizo
val AmareloAlerta: Color get() = paletaAtual.value.alerta
val AzulInfo: Color get() = paletaAtual.value.info
val Turquesa: Color get() = paletaAtual.value.turquesa
val Roxo: Color get() = paletaAtual.value.roxo

val Fundo: Color get() = paletaAtual.value.fundo
val Superficie: Color get() = paletaAtual.value.superficie
val SuperficieAlta: Color get() = paletaAtual.value.superficieAlta
val SuperficieMaisAlta: Color get() = paletaAtual.value.superficieMaisAlta
val TextoPrincipal: Color get() = paletaAtual.value.textoPrincipal
val TextoSecundario: Color get() = paletaAtual.value.textoSecundario
val Contorno: Color get() = paletaAtual.value.contorno

/** Uma cor por aplicativo, para gráficos e listas (as cores da marca de cada um). */
val CorUber: Color get() = paletaAtual.value.uber
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

// ------------------------------------------------------------------ preferência

enum class ModoTema(val rotulo: String, val descricao: String) {
    ESCURO("Escuro", "Painel de carro: fundo preto, números acesos. Melhor à noite."),
    CLARO("Claro", "Fundo branco. Melhor com o sol batendo na tela."),
    SISTEMA("Do sistema", "Acompanha o modo noturno do Android.")
}

/**
 * O modo escolhido, guardado em SharedPreferences.
 *
 * Resolve "do sistema" na hora de aplicar (e não durante a composição) para não
 * escrever estado enquanto a tela desenha — isso faria o Compose reclamar.
 */
object PreferenciaTema {

    private val estado = mutableStateOf(ModoTema.ESCURO)

    /** O que o motorista escolheu (pode ser SISTEMA). */
    val modo: ModoTema get() = estado.value

    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("aparencia", Context.MODE_PRIVATE)

    /**
     * Chamado uma vez no onCreate, antes de desenhar.
     *
     * O app nasceu claro e passou a ser escuro com o visual novo. Quem já usava
     * tinha "CLARO" gravado de quando era essa a única opção — e ficaria preso
     * no claro para sempre, sem nunca ver o tema novo. Então o padrão novo é
     * aplicado UMA vez, e a partir daí o que o motorista escolher manda.
     */
    fun carregar(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean(KEY_PADRAO_NOVO, false)) {
            p.edit().putBoolean(KEY_PADRAO_NOVO, true).remove(KEY).apply()
            aplicar(c, ModoTema.ESCURO)
            return
        }
        val salvo = p.getString(KEY, null)
        aplicar(c, runCatching { ModoTema.valueOf(salvo ?: "") }.getOrDefault(ModoTema.ESCURO))
    }

    fun definir(c: Context, m: ModoTema) {
        prefs(c).edit().putString(KEY, m.name).apply()
        aplicar(c, m)
    }

    private fun aplicar(c: Context, m: ModoTema) {
        estado.value = m
        val escuro = when (m) {
            ModoTema.ESCURO -> true
            ModoTema.CLARO -> false
            ModoTema.SISTEMA -> sistemaEstaEscuro(c)
        }
        escuroAtual.value = escuro
        paletaAtual.value = if (escuro) ESCURA else CLARA
    }

    private fun sistemaEstaEscuro(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private const val KEY = "modo"

    /** Marca que o padrão escuro já foi aplicado uma vez neste aparelho. */
    private const val KEY_PADRAO_NOVO = "padrao_escuro_aplicado"
}

// ------------------------------------------------------------------ esquemas

private val EsquemaEscuro = darkColorScheme(
    primary = ESCURA.lucro,
    onPrimary = ESCURA.fundo,
    primaryContainer = Color(0xFF14301A),
    onPrimaryContainer = ESCURA.lucro,
    secondary = ESCURA.turquesa,
    onSecondary = ESCURA.fundo,
    secondaryContainer = Color(0xFF11201C),
    onSecondaryContainer = Color(0xFFB6E8DF),
    tertiary = ESCURA.alerta,
    onTertiary = ESCURA.fundo,
    background = ESCURA.fundo,
    onBackground = ESCURA.textoPrincipal,
    surface = ESCURA.fundo,
    onSurface = ESCURA.textoPrincipal,
    surfaceVariant = ESCURA.superficieAlta,
    onSurfaceVariant = ESCURA.textoSecundario,
    surfaceContainerLowest = ESCURA.fundo,
    surfaceContainerLow = ESCURA.superficie,
    surfaceContainer = ESCURA.superficie,
    surfaceContainerHigh = ESCURA.superficieAlta,
    surfaceContainerHighest = ESCURA.superficieMaisAlta,
    outline = ESCURA.contorno,
    outlineVariant = ESCURA.contorno,
    error = ESCURA.prejuizo,
    onError = ESCURA.fundo
)

private val EsquemaClaro = lightColorScheme(
    primary = CLARA.lucro,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF3D0),
    onPrimaryContainer = CLARA.lucroEscuro,
    secondary = CLARA.turquesa,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EFEB),
    onSecondaryContainer = Color(0xFF0A5A52),
    tertiary = CLARA.alerta,
    onTertiary = Color.White,
    background = CLARA.fundo,
    onBackground = CLARA.textoPrincipal,
    surface = CLARA.fundo,
    onSurface = CLARA.textoPrincipal,
    surfaceVariant = CLARA.superficieMaisAlta,
    onSurfaceVariant = CLARA.textoSecundario,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CLARA.superficie,
    surfaceContainer = CLARA.superficie,
    surfaceContainerHigh = CLARA.superficieAlta,
    surfaceContainerHighest = CLARA.superficieMaisAlta,
    outline = CLARA.contorno,
    outlineVariant = CLARA.contorno,
    error = CLARA.prejuizo,
    onError = Color.White
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

@Composable
fun MotoristaTema(content: @Composable () -> Unit) {
    val esquema = if (escuroAtual.value) EsquemaEscuro else EsquemaClaro
    MaterialTheme(colorScheme = esquema, typography = Tipografia, content = content)
}
