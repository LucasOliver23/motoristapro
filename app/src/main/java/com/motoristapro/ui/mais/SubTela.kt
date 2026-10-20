package com.motoristapro.ui.mais

/**
 * As telas que a lista do "Mais" abre.
 *
 * A regra do corte: o que o motorista mexe TODO DIA fica como interruptor na
 * lista; o que ele configura UMA VEZ some para dentro de uma destas.
 */
enum class SubTela(val titulo: String) {
    PERFIL("Meu perfil"),
    VEICULO("Meu veículo e custos"),
    // Faixas e estilo do cartão viraram uma tela só: os dois são o semáforo —
    // um decide a cor, o outro decide o que o cartão mostra dentro dela.
    SEMAFORO("Semáforo de valores"),
    CHECKUP("Checkup do app"),
    RISCO("Endereços de risco"),
    LEITOR("Leitor de ofertas"),
    NUVEM("Conta e nuvem"),
    BACKUP("Backup e exportação"),
    VERSAO("Versão do app"),
    APARENCIA("Aparência do app"),
    ASSINATURA("Minha assinatura"),
    NAVEGACAO("App de navegação"),
    PACOTE("Pacote 0% de taxa"),
    ESTILO("Aparência do cartão"),
    METAS("Suas metas"),
    HORARIOS("Melhores horários"),
    RADAR("Radar de eventos"),
    COMPARAR("Comparar apps"),
    RELATORIO("Relatório do mês"),
    VEICULOS("Meus veículos"),
    MANUTENCAO("Manutenção")
}
