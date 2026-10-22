package com.motoristapro.custo

import com.motoristapro.data.prefs.PreferenciasApp
import com.motoristapro.data.repository.FinanceiroRepository
import com.motoristapro.data.repository.Periodo
import com.motoristapro.service.ParametrosRegua
import com.motoristapro.service.ReguaLucroHora
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Junta o que a régua do lucro por hora precisa, sempre atualizado: a meta
 * limpa por hora, o custo/km do veículo ativo e o que o app já mediu do
 * motorista (tempo com corrida no turno e velocidade nas corridas).
 *
 * Vive no MotoristaApp: o leitor de ofertas, o "Testar com print" e a tela do
 * semáforo leem daqui, para os três julgarem a corrida igual.
 */
class FonteRegua(repo: FinanceiroRepository, prefs: PreferenciasApp, escopo: CoroutineScope) {

    /** null = sem meta, sem horas ou sem custo/km: vale o semáforo antigo. */
    val parametros: StateFlow<ParametrosRegua?> = Periodo.ultimosDias(DIAS_DA_MEDIA).let { (ini, fim) ->
        combine(
            repo.configuracao(),
            combine(prefs.horasPorDia, repo.perfilCusto()) { escolhidas, perfil ->
                escolhidas ?: perfil?.horasPorDia
            },
            repo.resumoDoPeriodo(ini, fim),
            repo.totaisJornadasEncerradas(ini, fim),
            repo.ultimosTrechos()
        ) { cfg, horas, resumo, turnos, trechos ->
            if (horas == null || horas <= 0 || cfg.metaLucroDiarioCentavos <= 0 || cfg.custoKmCentavos <= 0) {
                return@combine null
            }
            ParametrosRegua(
                metaLimpaHoraCentavos = Math.round(cfg.metaLucroDiarioCentavos.toDouble() / horas),
                custoKmCentavos = cfg.custoKmCentavos,
                fracaoMedida = MetaPorHora.fracaoComPassageiro(resumo.segundosEmCorrida, turnos.segundos),
                velocidadeMedidaKmH = ReguaLucroHora.velocidadeEmCorrida(trechos.map { it.metros to it.segundos })
            )
        }
    }.stateIn(escopo, SharingStarted.Eagerly, null)

    private companion object {
        /** A mesma janela da meta por hora (Menu ▸ Suas metas). */
        const val DIAS_DA_MEDIA = 30
    }
}
