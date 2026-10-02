package com.motoristapro.assinatura

import java.util.concurrent.TimeUnit

/** Os planos que o motorista pode assinar. */
enum class Plano(val chave: String, val rotulo: String, val meses: Int) {
    MENSAL("mensal", "Mensal", 1),
    TRIMESTRAL("trimestral", "Trimestral", 3)
}

/**
 * Preço de um plano, lido da nuvem (documento `config/precos`).
 *
 * Fica na nuvem de propósito: mudar de R$ 14,90 para R$ 17,90 não pode exigir
 * uma versão nova do app na mão de todo mundo.
 */
data class PrecoPlano(
    val plano: Plano,
    val centavos: Long,
    /** Link de checkout do Mercado Pago deste plano. */
    val linkCheckout: String?
) {
    /** Quanto sai o mês dentro deste plano — é o número que vende o trimestral. */
    val porMesCentavos: Long get() = centavos / plano.meses

    companion object {
        /** Usado enquanto a nuvem não responde, para a tela nunca abrir vazia. */
        val PADRAO = listOf(
            PrecoPlano(Plano.MENSAL, 1_490, null),
            PrecoPlano(Plano.TRIMESTRAL, 3_990, null)
        )
    }
}

/** Em que situação está o acesso do motorista. */
enum class SituacaoAcesso {
    /** Ainda carregando do Firestore — não trave a tela aqui. */
    CARREGANDO,

    /** Sem Firebase configurado: o app roda inteiro, como antes do login existir. */
    SEM_CONTA,

    TESTE_ATIVO,
    ASSINATURA_ATIVA,
    TESTE_ACABOU,
    ASSINATURA_VENCIDA
}

/**
 * O acesso do motorista, do jeito que a tela precisa ver.
 *
 * As datas vêm do RELÓGIO DO SERVIDOR (o Firestore carimba `trialInicio` e a
 * função do Mercado Pago carimba `assinaturaAte`). O celular só compara — e se
 * o relógio dele estiver atrasado em relação ao último carimbo que já vimos,
 * a comparação usa o carimbo, não o celular. Sem isso, bastava voltar a data
 * do aparelho para ganhar teste infinito.
 */
data class Acesso(
    val situacao: SituacaoAcesso = SituacaoAcesso.CARREGANDO,
    val trialFimMs: Long = 0,
    val assinaturaAteMs: Long = 0,
    val plano: Plano? = null,
    val agoraMs: Long = 0
) {
    val liberado: Boolean
        get() = situacao == SituacaoAcesso.TESTE_ATIVO ||
            situacao == SituacaoAcesso.ASSINATURA_ATIVA ||
            situacao == SituacaoAcesso.SEM_CONTA ||
            situacao == SituacaoAcesso.CARREGANDO

    /** Dias que faltam no teste (0 quando já acabou). Arredonda para cima: o dia que começou conta. */
    val diasDeTeste: Int
        get() = restanteEmDias(trialFimMs)

    val diasDeAssinatura: Int
        get() = restanteEmDias(assinaturaAteMs)

    private fun restanteEmDias(fimMs: Long): Int {
        if (fimMs <= 0 || agoraMs <= 0) return 0
        val falta = fimMs - agoraMs
        if (falta <= 0) return 0
        return Math.ceil(falta.toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
    }

    /** Frase curta para a faixa do topo do app. */
    val aviso: String?
        get() = when (situacao) {
            SituacaoAcesso.TESTE_ATIVO ->
                if (diasDeTeste <= 3) "Seu teste acaba em $diasDeTeste dia(s)" else null
            SituacaoAcesso.ASSINATURA_ATIVA ->
                if (diasDeAssinatura <= 5) "Sua assinatura vence em $diasDeAssinatura dia(s)" else null
            else -> null
        }

    companion object {
        /** 14 dias de teste, contados do primeiro login. */
        const val DIAS_DE_TESTE = 14L
        val MS_DE_TESTE: Long get() = TimeUnit.DAYS.toMillis(DIAS_DE_TESTE)
    }
}
