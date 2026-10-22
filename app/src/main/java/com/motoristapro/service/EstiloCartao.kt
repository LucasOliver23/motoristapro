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
    PARADAS("paradas", "Paradas", "iFood e 99 Entrega: quantas entregas tem na rota");

    companion object {
        fun porChave(c: String): CampoCartao? = entries.firstOrNull { it.chave == c }

        /**
         * Cartão único (4.19): os números saem do TIPO da corrida, não de uma
         * escolha do motorista. Passageiro: lucro, R$/km e R$/hora (a nota entra
         * se faltar um). Entrega (paradas ou sem tempo no card): lucro, R$/km e
         * paradas (a distância entra se faltar um). O que a oferta não tem
         * (lucro sem custo, nota que a tela não mostrou) dá a vez ao seguinte.
         */
        fun daOferta(o: Oferta): List<CampoCartao> =
            if (o.paradas > 0 || o.minutos <= 0) listOf(LUCRO, REAIS_KM, PARADAS, DISTANCIA)
            else listOf(LUCRO, REAIS_KM, REAIS_HORA, NOTA)

        /** O que vem marcado de fábrica, na ordem em que aparece. */
        // Os 4 números do cartão branco (4.8): valor, tempo e km já vão na linha de cima.
        val PADRAO = listOf(REAIS_KM, REAIS_HORA, NOTA, LUCRO_PCT)
    }
}

/** Como o cartão se pinta. */
/** As três cores da decisão (boa / atenção / recusar). */
enum class PaletaCartao(val rotulo: String, val boa: Int, val media: Int, val ruim: Int) {
    CLASSICA("Clássica", 0xFF22A06B.toInt(), 0xFFE8A800.toInt(), 0xFFE5484D.toInt()),
    AZUL("Azul do app", 0xFF4CC9F0.toInt(), 0xFFF97316.toInt(), 0xFFE5484D.toInt()),
    // Azul / amarelo / roxo: as três se distinguem também para quem não vê verde e vermelho.
    DALTONICO("Daltônico", 0xFF2F80ED.toInt(), 0xFFF2C94C.toInt(), 0xFF9B51E0.toInt());

    fun cor(c: Classificacao): Int = when (c) {
        Classificacao.BOA -> boa
        Classificacao.MEDIA -> media
        Classificacao.RUIM -> ruim
    }
}

/** Um número do cartão já pronto para desenhar: "R$/km" + "1,82". */
data class NumeroCartao(val campo: CampoCartao, val rotulo: String, val valor: String)

enum class TemaCartao(val rotulo: String) {
    NOVO("Branco"),         // cartão branco com a etiqueta ACEITAR/ATENÇÃO/RECUSAR — o padrão (4.7)
    ESCURO("Escuro"),       // o mesmo cartão em fundo escuro, borda na cor da decisão (4.8)
    COLORIDO("Colorido"),   // fundo na cor do semáforo
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
    val tema: TemaCartao = TemaCartao.NOVO,
    val tamanho: TamanhoCartao = TamanhoCartao.M,
    val posicao: PosicaoCartao = PosicaoCartao.TOPO,
    /** 40 a 100. Abaixo de 100 dá para enxergar o app de corrida por baixo. */
    val opacidade: Int = 100,
    /** 5 a 60 segundos até o cartão sumir sozinho. */
    val segundosNaTela: Int = 15,
    /** Mostrar de qual app veio a oferta (Uber, 99, iFood). */
    val mostrarApp: Boolean = true,
    /** A faixa com ACEITAR / ATENÇÃO / RECUSAR em cima do cartão. */
    val avisoEmDestaque: Boolean = true,
    val paleta: PaletaCartao = PaletaCartao.CLASSICA,
    /**
     * Botões Registrar / Embarque / Destino no cartão. Ligado por padrão (5.1.3):
     * sem o Registrar a corrida aceita não entra em Corridas.
     */
    val botoes: Boolean = true
) {

    /**
     * Os números do cartão branco/escuro, na ordem escolhida: no máximo 4, e só
     * os que esta oferta tem (sem minutos não há R$/hora; sem custo, não há lucro).
     */
    fun numeros(o: Oferta, custoKmCentavos: Long): List<NumeroCartao> =
        CampoCartao.daOferta(o).mapNotNull { campo ->
            val v: String? = when (campo) {
                CampoCartao.REAIS_KM -> num(o.reaisPorKm, 2)
                CampoCartao.REAIS_HORA -> if (o.minutos > 0) num(o.reaisPorHora, 0) else null
                CampoCartao.LUCRO -> if (custoKmCentavos > 0) num(o.lucroEstimado(custoKmCentavos), 2) else null
                CampoCartao.LUCRO_PCT ->
                    if (custoKmCentavos > 0) "${o.lucroPercentual(custoKmCentavos).toInt()}%" else null
                CampoCartao.NOTA -> o.nota?.let { num(it, 2) }
                CampoCartao.VALOR -> num(o.valor, 2)
                CampoCartao.DISTANCIA -> num(o.km, 1)
                CampoCartao.TEMPO -> if (o.minutos > 0) "${o.minutos}" else null
                CampoCartao.REAIS_MIN -> if (o.minutos > 0) num(o.reaisPorMinuto, 2) else null
                CampoCartao.PARADAS -> if (o.paradas > 1) "${o.paradas}" else null
            }
            v?.let { NumeroCartao(campo, rotuloCurto(campo), it) }
        }.take(3)

    private fun num(v: Double, casas: Int): String? =
        if (v.isFinite()) String.format(PT, "%.${casas}f", v) else null

    private fun rotuloCurto(c: CampoCartao): String = when (c) {
        CampoCartao.REAIS_KM -> "R$/km"
        CampoCartao.REAIS_HORA -> "R$/hora"
        CampoCartao.LUCRO -> "Lucro R$"
        CampoCartao.LUCRO_PCT -> "Lucro"
        CampoCartao.NOTA -> "Nota"
        CampoCartao.VALOR -> "Valor"
        CampoCartao.DISTANCIA -> "Km"
        CampoCartao.TEMPO -> "Min"
        CampoCartao.REAIS_MIN -> "R$/min"
        CampoCartao.PARADAS -> "Paradas"
    }


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
        camposComValor(o, custoKmCentavos).map { it.second }

    /**
     * O mesmo que [valores], mas dizendo de qual campo veio cada texto.
     *
     * Serve para pintar cada número na cor da sua própria faixa: sem saber o
     * campo, o cartão só consegue pintar tudo de uma cor só.
     */
    fun camposComValor(o: Oferta, custoKmCentavos: Long): List<Pair<CampoCartao, String>> =
        CampoCartao.daOferta(o).mapNotNull { campo ->
            val texto = when (campo) {
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
            texto?.let { campo to it }
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
        // 4.7: o cartão branco entra uma vez para todo mundo, mesmo para quem já
        // tinha escolhido outro tema — depois disso a escolha do motorista manda.
        if (!prefs.getBoolean(KEY_NOVO_APLICADO, false)) {
            prefs.edit().putString(KEY_TEMA, TemaCartao.NOVO.name).putBoolean(KEY_NOVO_APLICADO, true).apply()
        }
        // 4.8: o cartão branco passa a mostrar os números escolhidos; a lista volta
        // uma vez ao padrão novo (R$/km, R$/hora, Nota, Lucro %) aprovado no desenho.
        if (!prefs.getBoolean(KEY_CAMPOS_48, false)) {
            prefs.edit().remove(KEY_CAMPOS).putBoolean(KEY_CAMPOS_48, true).apply()
        }
        // 5.1.3: os botões (Registrar / Embarque / Destino) ligam uma vez para todo
        // mundo — quem já tinha mexido na Aparência ficou com "desligado" gravado.
        // Depois disso a escolha do motorista manda.
        if (!prefs.getBoolean(KEY_BOTOES_513, false)) {
            prefs.edit().putBoolean(KEY_BOTOES, true).putBoolean(KEY_BOTOES_513, true).apply()
        }
        val padrao = ConfigCartao()
        val salvos = prefs.getString(KEY_CAMPOS, null)
            ?.split(",")
            ?.mapNotNull { CampoCartao.porChave(it.trim()) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
        // 4.19: cartão único. O desenho é sempre o escuro com a borda na cor da
        // decisão; da escolha antiga ficam tamanho, posição, tempo, botões e a
        // paleta para daltônico (a "Azul do app" volta para a clássica).
        val paleta = enumOu(KEY_PALETA, padrao.paleta) { PaletaCartao.valueOf(it) }
        return ConfigCartao(
            campos = salvos ?: padrao.campos,
            tema = TemaCartao.ESCURO,
            tamanho = enumOu(KEY_TAMANHO, padrao.tamanho) { TamanhoCartao.valueOf(it) },
            posicao = enumOu(KEY_POSICAO, padrao.posicao) { PosicaoCartao.valueOf(it) },
            opacidade = 100,
            segundosNaTela = prefs.getInt(KEY_SEGUNDOS, padrao.segundosNaTela).coerceIn(5, 60),
            mostrarApp = padrao.mostrarApp,
            avisoEmDestaque = true,
            paleta = if (paleta == PaletaCartao.DALTONICO) PaletaCartao.DALTONICO else PaletaCartao.CLASSICA,
            botoes = prefs.getBoolean(KEY_BOTOES, padrao.botoes)
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
            .putString(KEY_PALETA, c.paleta.name)
            .putBoolean(KEY_BOTOES, c.botoes)
            .apply()
    }

    fun restaurarPadrao(): ConfigCartao {
        prefs.edit().clear().putBoolean(KEY_NOVO_APLICADO, true).putBoolean(KEY_CAMPOS_48, true)
            .putBoolean(KEY_BOTOES_513, true).apply()
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
        const val KEY_NOVO_APLICADO = "tema_novo_aplicado"
        const val KEY_CAMPOS_48 = "campos_4_8"
        const val KEY_PALETA = "paleta"
        const val KEY_BOTOES = "botoes"
        const val KEY_BOTOES_513 = "botoes_5_1_3"
    }
}
