package com.motoristapro.ui.mais

/**
 * As telas que a lista do "Mais" abre.
 *
 * Desde a 4.18 o Menu mostra só 8 (perfil, leitor, veículos, metas, copiloto,
 * conta, assinatura e ajustes); as outras abrem de dentro delas ([pai]).
 * Melhores horários, Comparar apps e Relatório do mês moram na aba Atividade
 * (continuam aqui para os atalhos antigos funcionarem).
 */
enum class SubTela(val titulo: String) {
    PERFIL("Meu perfil"),
    VEICULO("Meu veículo e custos"),
    // Faixas e estilo do cartão viraram uma tela só: os dois são o semáforo —
    // um decide a cor, o outro decide o que o cartão mostra dentro dela.
    SEMAFORO("Semáforo de valores"),
    COPILOTO("Copiloto"),
    RISCO("Endereços de risco"),
    LEITOR("Leitor de ofertas"),
    TESTE_PRINT("Testar com print"),
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
    COMPARAR("Comparar apps"),
    RELATORIO("Relatório do mês"),
    VEICULOS("Meus veículos"),
    MANUTENCAO("Manutenção"),
    AJUSTES("Ajustes");

    /**
     * A tela de onde esta saiu (4.18): o Menu tem 8 linhas e as outras telas
     * moram dentro delas. Voltar leva para a de cima, não direto para o Menu.
     */
    val pai: SubTela?
        get() = when (this) {
            SEMAFORO, ESTILO, RISCO, NAVEGACAO, TESTE_PRINT, PACOTE -> LEITOR
            VEICULO, MANUTENCAO -> VEICULOS
            BACKUP -> NUVEM
            VERSAO, APARENCIA -> AJUSTES
            else -> null
        }
}
