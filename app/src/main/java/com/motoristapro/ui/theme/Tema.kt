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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.motoristapro.R

/**
 * Paleta (5.0): preto absoluto em três camadas (escuro) ou branco em três camadas
 * (claro). A COR só aparece quando significa alguma coisa: verde é lucro e
 * "aceitar", amarelo é atenção, vermelho é prejuízo e "recusar". Botões, abas,
 * seleção e ícones usam o DESTAQUE, que é branco no escuro e preto no claro.
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

val FundoApp = Color(0xFF000000)
val FundoCard = Color(0xFF121212)
val VermelhoDespesa = Color(0xFFFF5252)
val FundoBotaoEscuro = Color(0xFF1C1C1E)

private class Paleta(
    /** Verde do lucro e do "aceitar". Só para dinheiro e decisão. */
    val lucro: Color,
    /** Destaque neutro: botões, aba aberta, seleção, ícones. */
    val destaque: Color,
    /** Destaque apagado (trilhas, bordas do que está selecionado). */
    val destaqueEscuro: Color,
    /** Texto e ícone em cima do destaque. */
    val sobreDestaque: Color,
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
    // Padrão do Oliver: preto absoluto, cartões #121212 / #1c1c1e, verde #00d084
    // — e o verde só no dinheiro e na decisão; o resto é branco.
    lucro = Color(0xFF00D084),
    destaque = Color(0xFFFFFFFF),
    destaqueEscuro = Color(0xFFBDBDBD),
    sobreDestaque = Color(0xFF000000),
    prejuizo = VermelhoDespesa,
    alerta = Color(0xFFFFC94D),
    info = Color(0xFF7AA7E0),
    turquesa = Color(0xFFE5E5E5),
    roxo = Color(0xFFB79BE8),
    fundo = FundoApp,
    superficie = FundoCard,
    superficieAlta = FundoBotaoEscuro,
    superficieMaisAlta = Color(0xFF2C2C2E),
    textoPrincipal = Color(0xFFFFFFFF),
    textoSecundario = Color(0xFF9A9A9A),
    // Contorno quase do tom do cartão: separa sem desenhar moldura.
    contorno = Color(0xFF262626),
    uber = Color(0xFFFFFFFF)
)

// No claro as cores de significado precisam ESCURECER: lima sobre branco não se lê.
// O tom muda, o significado não — azul continua lucro, vermelho continua prejuízo.
private val CLARA = Paleta(
    // Verde mais fechado: o #00d084 some no fundo branco. Destaque vira preto.
    lucro = Color(0xFF047857),
    destaque = Color(0xFF111111),
    destaqueEscuro = Color(0xFF444444),
    sobreDestaque = Color(0xFFFFFFFF),
    prejuizo = Color(0xFFDC2626),
    alerta = Color(0xFFB45309),
    info = Color(0xFF2563EB),
    turquesa = Color(0xFF333333),
    roxo = Color(0xFF6A32B0),
    fundo = Color(0xFFF5F5F5),
    superficie = Color(0xFFFFFFFF),
    superficieAlta = Color(0xFFFFFFFF),
    superficieMaisAlta = Color(0xFFE5E5E5),
    textoPrincipal = Color(0xFF111111),
    textoSecundario = Color(0xFF6B6B6B),
    contorno = Color(0xFFE5E5E5),
    uber = Color(0xFF111111)
)

private val paletaAtual = mutableStateOf(ESCURA)
private val escuroAtual = mutableStateOf(true)

/** true quando o app está desenhando no modo escuro (já resolvendo "do sistema"). */
val temaEscuro: Boolean get() = escuroAtual.value

/**
 * O DESTAQUE (5.0): branco no escuro, preto no claro. O nome ficou do tempo em
 * que o destaque era verde-limão, para não mexer em 250 telas; dinheiro e
 * decisão usam [VerdeLucro].
 */
val Lima: Color get() = paletaAtual.value.destaque
val LimaEscuro: Color get() = paletaAtual.value.destaqueEscuro
val SobreDestaque: Color get() = paletaAtual.value.sobreDestaque

/** Verde do lucro e do "aceitar": só para dinheiro positivo e decisão boa. */
val VerdeLucro: Color get() = paletaAtual.value.lucro
val VermelhoPrejuizo: Color get() = paletaAtual.value.prejuizo
val AmareloAlerta: Color get() = paletaAtual.value.alerta
val AzulInfo: Color get() = paletaAtual.value.info
val Turquesa: Color get() = paletaAtual.value.turquesa
val Roxo: Color get() = paletaAtual.value.roxo

/** Laranja de combustível e manutenção nos selos do Menu e de Finanças. Igual nos dois temas. */
val Laranja = Color(0xFFF97316)

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
        // O visual novo (4.0) foi desenhado no escuro: quem estava no claro
        // passa para o escuro UMA vez, e pode voltar em Menu ▸ Aparência.
        if (!p.getBoolean(KEY_PADRAO_NOVO, false) || !p.getBoolean(KEY_VISUAL_NOVO, false)) {
            p.edit().putBoolean(KEY_PADRAO_NOVO, true).putBoolean(KEY_VISUAL_NOVO, true).remove(KEY).apply()
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

    /** Marca que o escuro do visual novo (4.0) já foi aplicado uma vez. */
    private const val KEY_VISUAL_NOVO = "visual_novo_4_aplicado"
}

// ------------------------------------------------------------------ esquemas

private val EsquemaEscuro = darkColorScheme(
    primary = ESCURA.destaque,
    onPrimary = ESCURA.sobreDestaque,
    primaryContainer = ESCURA.superficieMaisAlta,
    onPrimaryContainer = ESCURA.destaque,
    secondary = ESCURA.destaque,
    onSecondary = ESCURA.sobreDestaque,
    secondaryContainer = ESCURA.superficieAlta,
    onSecondaryContainer = ESCURA.textoPrincipal,
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
    primary = CLARA.destaque,
    onPrimary = CLARA.sobreDestaque,
    primaryContainer = CLARA.superficieMaisAlta,
    onPrimaryContainer = CLARA.destaque,
    secondary = CLARA.destaque,
    onSecondary = CLARA.sobreDestaque,
    secondaryContainer = CLARA.superficieMaisAlta,
    onSecondaryContainer = CLARA.textoPrincipal,
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
 * Fonte do texto: Inter (licença OFL), em res/font. Desenhada para tela, com o
 * "1" e o "l" bem diferentes — ajuda a ler endereço e valor de relance.
 */
val FonteTexto = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

/**
 * Fonte dos números grandes (lucro do dia, valor da oferta): a mesma Inter (5.0),
 * em ExtraBold. Uma fonte só no app inteiro; a Space Grotesk saiu.
 */
val FonteNumeros = FontFamily(
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

/** Estilo pronto para um valor de destaque: Inter ExtraBold, algarismos de largura fixa. */
val EstiloValor = TextStyle(
    fontFamily = FonteNumeros,
    fontWeight = FontWeight.ExtraBold,
    fontFeatureSettings = "tnum"
)

/**
 * Tipografia: Inter em tudo, com os ALGARISMOS DE LARGURA FIXA
 * ("tnum") nos estilos usados para dinheiro.
 *
 * Sem isso, o 1 é mais estreito que o 8 e o valor "pula" de lado toda vez que o
 * lucro do dia atualiza — some com a sensação de painel e cansa a vista no trânsito.
 */
private val Numeros = TextStyle(fontFeatureSettings = "tnum")

private val Inter = TextStyle(fontFamily = FonteTexto)

private val Tipografia = Typography().run {
    copy(
        // display* no app são só números grandes: Inter ExtraBold com tnum.
        displayLarge = displayLarge.merge(EstiloValor),
        displayMedium = displayMedium.merge(EstiloValor),
        displaySmall = displaySmall.merge(EstiloValor),
        headlineLarge = headlineLarge.merge(Inter).merge(Numeros),
        headlineMedium = headlineMedium.merge(Inter).merge(Numeros),
        headlineSmall = headlineSmall.merge(Inter).merge(Numeros),
        titleLarge = titleLarge.merge(Inter).merge(Numeros),
        titleMedium = titleMedium.merge(Inter),
        titleSmall = titleSmall.merge(Inter),
        bodyLarge = bodyLarge.merge(Inter).merge(Numeros),
        bodyMedium = bodyMedium.merge(Inter),
        bodySmall = bodySmall.merge(Inter),
        labelLarge = labelLarge.merge(Inter),
        labelMedium = labelMedium.merge(Inter),
        labelSmall = labelSmall.merge(Inter)
    )
}

@Composable
fun MotoristaTema(content: @Composable () -> Unit) {
    val esquema = if (escuroAtual.value) EsquemaEscuro else EsquemaClaro
    MaterialTheme(colorScheme = esquema, typography = Tipografia, content = content)
}
