package com.motoristapro.service

import android.content.Context
import java.util.Locale

private val PT = Locale("pt", "BR")

/** Um número que pode aparecer no cartão da oferta. */
enum class CampoCartao(val chave: String, val rotulo: String, val explicacao: String) {
    REAIS_KM("rkm", "R$/km", "O mais importante: quanto a corrida paga por quilômetro"),
    REAIS_HORA("rhora", "R$/hora", "Quanto a corrida paga por hora do seu turno"),
    LUCRO("lucro", "Lucro em R$", "O que sobra depois de descontar o seu custo por km"),
    LUCRO_PCT("lucropct", "Lucro em %", "Quanto por cento do valor é lucro"),
    NOTA("nota", "Nota do passageiro", "Só aparece quando o app mostra a nota na tela"),
    VALOR("valor", "Valor da corrida", "O valor cheio que o app está oferecendo"),
    DISTANCIA("dist", "Distância total", "Até o passageiro mais a viagem"),
    TEMPO("tempo", "Tempo total", "Minutos previstos pelo app"),
    REAIS_MIN("rmin", "R$/min", "Para quem prefere pensar por minuto"),
    PARADAS("paradas", "Paradas", "iFood: quantas entregas tem na rota");

    companion object {
        fun porChave(c: String): CampoCartao? = entries.firstOrNull { it.chave == c }

        /** O que vem marcado de fábrica, na ordem em que aparece. */
        val PADRAO = listOf(REAIS_KM, REAIS_HORA, LUCRO, DISTANCIA, TEMPO)
    }
}

/** Como o cartão se pinta. */
enum class TemaCartao(val rotulo: String) {
    COLORIDO("Colorido"),   // fundo na cor do semáforo — o padrão
    ESCURO("Escuro"),
    CLARO("Claro")
}

enum class TamanhoCartao(val rotulo: String, val escala: Float) {
    PP("PP", 0.80f),
    P("P", 0.90f),
    M("M", 1.00f),
    G("G", 1.15f)
}

enum class PosicaoCartao(val rotulo: String) {
    TOPO("Topo"),
    CENTRO("Centro"),
    BAIXO("Embaixo")
}

/**
 * As escolhas do motorista sobre o cartão da oferta, como um valor só.
 *
 * É imutável de propósito: a tela de ajustes guarda uma cópia, cada toque gera
 * outra cópia (`copy`) e o Compose redesenha o preview na hora. Quem salva é o
 * [EstiloCartao].
 */
data class ConfigCartao(
    /** Campos visíveis, na ordem em que o motorista quer ler. */
    val campos: List<CampoCartao> = CampoCartao.PADRAO,
    val tema: TemaCartao = TemaCartao.COLORIDO,
    val tamanho: TamanhoCartao = TamanhoCartao.M,
    val posicao: PosicaoCartao = PosicaoCartao.TOPO,
    /** 40 a 100. Abaixo de 100 dá para enxergar o app de corrida por baixo. */
    val opacidade: Int = 100,
    /** 5 a 60 segundos até o cartão sumir sozinho. */
    val segundosNaTela: Int = 15,
    /** Mostrar de qual app veio a oferta (Uber, 99, iFood). */
    val mostrarApp: Boolean = true,
    /** A faixa com ACEITAR / ATENÇÃO / RECUSAR em cima do cartão. */
    val avisoEmDestaque: Boolean = true
) {

    /** Liga ou desliga um campo. O último não pode sair: cartão vazio não serve. */
    fun alternando(c: CampoCartao): ConfigCartao = when {
        c !in campos -> copy(campos = campos + c)
        campos.size > 1 -> copy(campos = campos - c)
        else -> this
    }

    /** Move o campo na ordem de leitura (-1 sobe, +1 desce). */
    fun movendo(c: CampoCartao, passo: Int): ConfigCartao {
        val lista = campos.toMutableList()
        val de = lista.indexOf(c)
        val para = de + passo
        if (de < 0 || para < 0 || para > lista.lastIndex) return this
        lista[de] = lista[para]
        lista[para] = c
        return copy(campos = lista)
    }

    val duracaoMs: Long get() = segundosNaTela * 1000L

    /**
     * Os textos que o cartão mostra, na ordem escolhida.
     *
     * Cada um carrega a sua unidade ("R$ 1,67/km", "26 min"): no meio do trânsito
     * o motorista não tem tempo de adivinhar a qual rótulo pertence qual número.
     * Campo que não faz sentido nesta oferta (nota que a tela não mostrou, lucro
     * sem custo cadastrado) some da lista em vez de aparecer vazio.
     */
    fun valores(o: Oferta, custoKmCentavos: Long): List<String> =
        campos.mapNotNull { campo ->
            when (campo) {
                CampoCartao.REAIS_KM -> moeda(o.reaisPorKm) + "/km"
                CampoCartao.REAIS_HORA -> if (o.minutos > 0) moeda(o.reaisPorHora) + "/h" else null
                CampoCartao.LUCRO ->
                    if (custoKmCentavos > 0) moeda(o.lucroEstimado(custoKmCentavos)) + " lucro" else null
                CampoCartao.LUCRO_PCT ->
                    if (custoKmCentavos > 0) "${o.lucroPercentual(custoKmCentavos).toInt()}% lucro" else null
                CampoCartao.NOTA -> o.nota?.let { String.format(PT, "★ %.2f", it) }
                CampoCartao.VALOR -> moeda(o.valor)
                CampoCartao.DISTANCIA -> String.format(PT, "%.1f km", o.km)
                CampoCartao.TEMPO -> if (o.minutos > 0) "${o.minutos} min" else null
                CampoCartao.REAIS_MIN -> if (o.minutos > 0) moeda(o.reaisPorMinuto) + "/min" else null
                CampoCartao.PARADAS -> if (o.paradas > 1) "${o.paradas} paradas" else null
            }
        }

    private fun moeda(v: Double): String =
        if (v.isFinite()) String.format(PT, "R$ %.2f", v) else "—"
}

/**
 * Guarda e devolve a [ConfigCartao].
 *
 * SharedPreferences porque é lido pelo serviço de acessibilidade a cada oferta —
 * nenhuma consulta ao banco no caminho crítico. A ORDEM dos campos vai junto
 * (lista separada por vírgula), então reordenar é só salvar noutra ordem.
 */
class EstiloCartao(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("estilo_cartao", Context.MODE_PRIVATE)

    fun ler(): ConfigCartao {
        val padrao = ConfigCartao()
        val salvos = prefs.getString(KEY_CAMPOS, null)
            ?.split(",")
            ?.mapNotNull { CampoCartao.porChave(it.trim()) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
        return ConfigCartao(
            campos = salvos ?: padrao.campos,
            tema = enumOu(KEY_TEMA, padrao.tema) { TemaCartao.valueOf(it) },
            tamanho = enumOu(KEY_TAMANHO, padrao.tamanho) { TamanhoCartao.valueOf(it) },
            posicao = enumOu(KEY_POSICAO, padrao.posicao) { PosicaoCartao.valueOf(it) },
            opacidade = prefs.getInt(KEY_OPACIDADE, padrao.opacidade).coerceIn(40, 100),
            segundosNaTela = prefs.getInt(KEY_SEGUNDOS, padrao.segundosNaTela).coerceIn(5, 60),
            mostrarApp = prefs.getBoolean(KEY_APP, padrao.mostrarApp),
            avisoEmDestaque = prefs.getBoolean(KEY_AVISO, padrao.avisoEmDestaque)
        )
    }

    fun salvar(c: ConfigCartao) {
        prefs.edit()
            .putString(KEY_CAMPOS, c.campos.joinToString(",") { it.chave })
            .putString(KEY_TEMA, c.tema.name)
            .putString(KEY_TAMANHO, c.tamanho.name)
            .putString(KEY_POSICAO, c.posicao.name)
            .putInt(KEY_OPACIDADE, c.opacidade)
            .putInt(KEY_SEGUNDOS, c.segundosNaTela)
            .putBoolean(KEY_APP, c.mostrarApp)
            .putBoolean(KEY_AVISO, c.avisoEmDestaque)
            .apply()
    }

    fun restaurarPadrao(): ConfigCartao {
        prefs.edit().clear().apply()
        return ConfigCartao()
    }

    private fun <T> enumOu(chave: String, padrao: T, converter: (String) -> T): T =
        runCatching { converter(prefs.getString(chave, null) ?: "") }.getOrDefault(padrao)

    private companion object {
        const val KEY_CAMPOS = "campos"
        const val KEY_TEMA = "tema"
        const val KEY_TAMANHO = "tamanho"
        const val KEY_POSICAO = "posicao"
        const val KEY_OPACIDADE = "opacidade"
        const val KEY_SEGUNDOS = "segundos"
        const val KEY_APP = "mostrar_app"
        const val KEY_AVISO = "aviso_destaque"
    }
}
