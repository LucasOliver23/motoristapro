package com.motoristapro.service

import android.content.Context

/** Como o app de corrida está, pelo que o leitor viu na tela dele. */
enum class StatusApp(val rotulo: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    /** Nunca apareceu na tela, ou a tela não deu para entender. */
    DESCONHECIDO("—")
}

/** Um app de corrida acompanhado pela Jornada. */
enum class AppDeCorrida(val pacote: String, val rotulo: String, val sigla: String) {
    UBER("com.ubercab.driver", "Uber", "U"),
    NOVE9("com.app99.driver", "99", "99"),
    IFOOD("br.com.ifood.driver.app", "iFood", "iF"),
    INDRIVE(OfertaInDrive.PACOTE, "inDrive", "iD");

    companion object {
        fun porPacote(p: String?): AppDeCorrida? = entries.firstOrNull { it.pacote == p }
    }
}

/**
 * Descobre se cada app de corrida está Online ou Offline, lendo a tela dele.
 *
 * LIMITE QUE VALE SABER: a acessibilidade só enxerga o app que está NA FRENTE.
 * Enquanto o motorista olha a 99, não dá para ver a tela da Uber. Então o que
 * fica guardado é o ÚLTIMO estado visto de cada app, com a hora — e a tela
 * mostra "visto há X min" para ninguém confundir memória com tempo real.
 *
 * Por isso também existe a correção na mão: toque no app e ele troca de estado.
 * Em app que o leitor ainda não sabe interpretar, é a correção que vale.
 */
class StatusApps(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("status_apps", Context.MODE_PRIVATE)

    /**
     * As pistas de estado, NA ORDEM, e POR APP.
     *
     * Duas lições das telas reais:
     *
     * 1. **Os botões falam do que VAI acontecer.** "Ficar online" só existe
     *    quando se está OFFLINE, e contém a palavra "online". Lendo as palavras
     *    soltas primeiro, toda tela de app offline viraria online.
     *
     * 2. **Cada app fala uma língua.** O inDrive tem um botão "Online/Offline"
     *    no topo e pronto — mas a tela dele também diz "Procurando pedidos
     *    perto..." enquanto o motorista está OFFLINE. Uma regra genérica com
     *    "procurando" marcaria online o tempo todo. Por isso o inDrive usa só o
     *    botão dele, e a regra genérica vale para quem não tem regra própria.
     */
    private val pistasPorApp: Map<AppDeCorrida, List<Pair<Regex, StatusApp>>> = mapOf(
        // inDrive: só o botão do topo. É o único confirmado em tela.
        AppDeCorrida.INDRIVE to listOf(
            Regex("""\boffline\b""", RegexOption.IGNORE_CASE) to StatusApp.OFFLINE,
            Regex("""\bonline\b""", RegexOption.IGNORE_CASE) to StatusApp.ONLINE
        ),
        // 99: "Desconectar" é o botão de SAIR do ar — logo, está no ar.
        AppDeCorrida.NOVE9 to listOf(
            Regex("""\bdesconectar\b|sair\s+do\s+ar""", RegexOption.IGNORE_CASE) to StatusApp.ONLINE,
            Regex("""\bconectar\b|conecte-se\s+para\s+(aceitar|receber)|ficar\s+online""",
                RegexOption.IGNORE_CASE) to StatusApp.OFFLINE
        )
    )

    /** Para Uber, iFood e qualquer app sem regra própria. */
    private val pistasGerais: List<Pair<Regex, StatusApp>> = listOf(
        Regex("""ficar\s+online|fique\s+online|come[çc]ar?\s+a\s+(dirigir|receber)|entrar\s+no\s+ar""",
            RegexOption.IGNORE_CASE) to StatusApp.OFFLINE,
        Regex("""ficar\s+offline|fique\s+offline|sair\s+do\s+ar|desconectar""",
            RegexOption.IGNORE_CASE) to StatusApp.ONLINE,
        Regex("""voc[êe]\s+est[áa]\s+offline|voc[êe]\s+saiu\s+do\s+ar""",
            RegexOption.IGNORE_CASE) to StatusApp.OFFLINE,
        Regex("""voc[êe]\s+est[áa]\s+online|procurando\s+(viagens|corridas)|\bdispon[íi]vel\b""",
            RegexOption.IGNORE_CASE) to StatusApp.ONLINE,
        Regex("""\boffline\b""", RegexOption.IGNORE_CASE) to StatusApp.OFFLINE,
        Regex("""\bonline\b""", RegexOption.IGNORE_CASE) to StatusApp.ONLINE
    )

    /** Lê o estado do app a partir dos textos da tela dele. null = não deu para saber. */
    fun lerDaTela(pacote: String, textos: List<String>): StatusApp? {
        val app = AppDeCorrida.porPacote(pacote) ?: return null
        val tudo = textos.joinToString("\n")
        val pistas = pistasPorApp[app] ?: pistasGerais
        val status = pistas.firstOrNull { it.first.containsMatchIn(tudo) }?.second ?: return null
        definir(app, status, automatico = true)
        return status
    }

    fun status(app: AppDeCorrida): StatusApp =
        runCatching { StatusApp.valueOf(prefs.getString(chave(app), "") ?: "") }
            .getOrDefault(StatusApp.DESCONHECIDO)

    /** Quando este app foi visto por último (epoch ms). 0 = nunca. */
    fun vistoEm(app: AppDeCorrida): Long = prefs.getLong(chaveHora(app), 0)

    /** true quando o estado foi corrigido na mão e ainda não foi confirmado pela tela. */
    fun corrigidoNaMao(app: AppDeCorrida): Boolean = prefs.getBoolean(chaveMao(app), false)

    fun definir(app: AppDeCorrida, status: StatusApp, automatico: Boolean) {
        // Correção na mão não é atropelada por leitura automática do mesmo valor:
        // só uma leitura DIFERENTE (o app mudou mesmo) tira a marca.
        if (automatico && corrigidoNaMao(app) && status == status(app)) return
        prefs.edit()
            .putString(chave(app), status.name)
            .putLong(chaveHora(app), System.currentTimeMillis())
            .putBoolean(chaveMao(app), !automatico)
            .apply()
    }

    /** O toque do motorista no quadradinho do app: alterna entre Online e Offline. */
    fun alternar(app: AppDeCorrida) {
        val novo = if (status(app) == StatusApp.ONLINE) StatusApp.OFFLINE else StatusApp.ONLINE
        definir(app, novo, automatico = false)
    }

    /** Algum app está online? É o que separa "Offline" de "Aguardando viagens". */
    fun algumOnline(): Boolean = AppDeCorrida.entries.any { status(it) == StatusApp.ONLINE }

    fun online(): List<AppDeCorrida> =
        AppDeCorrida.entries.filter { status(it) == StatusApp.ONLINE }

    /** Fim do turno: ninguém fica "online" de um dia para o outro. */
    fun zerarTudo() {
        prefs.edit().clear().apply()
    }

    private fun chave(app: AppDeCorrida) = "status_${app.name}"
    private fun chaveHora(app: AppDeCorrida) = "visto_${app.name}"
    private fun chaveMao(app: AppDeCorrida) = "mao_${app.name}"
}
