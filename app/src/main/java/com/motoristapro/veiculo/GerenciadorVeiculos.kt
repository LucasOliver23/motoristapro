package com.motoristapro.veiculo

import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.entity.Veiculo
import kotlinx.coroutines.flow.first

/**
 * Troca de veículo ativo.
 *
 * O resto do app (semáforo, lucro, meta por hora) lê o custo por km, o consumo
 * e o nome da CONFIGURAÇÃO. Em vez de mexer em tudo isso, a troca guarda os
 * números do veículo que sai na linha dele e põe os do veículo que entra na
 * configuração: cada veículo leva o seu custo, e o resto do app nem percebe.
 */
class GerenciadorVeiculos(private val app: MotoristaApp) {

    private val repo get() = app.repository

    suspend fun ativar(id: Long) {
        val atual = app.preferencias.veiculoAtivo.value
        if (id == atual) return
        val novo = repo.obterVeiculo(id) ?: return
        sincronizarAtivo()
        val cfg = repo.obterConfiguracao()
        repo.salvarConfiguracao(
            cfg.copy(
                veiculoNome = novo.nome,
                consumoKmLx100 = novo.consumoKmLx100,
                precoLitroCentavos = novo.precoLitroCentavos,
                custoKmCentavos = novo.custoKmCentavos,
                tarifaMinimaCentavos = novo.tarifaMinimaCentavos
            )
        )
        app.preferencias.definirVeiculoAtivo(id)
        app.odometro.avisarTroca()
    }

    /** Copia o que está na configuração (assistente, perfil) para a linha do veículo ativo. */
    suspend fun sincronizarAtivo() {
        val id = app.preferencias.veiculoAtivo.value
        val v = repo.obterVeiculo(id) ?: return
        val cfg = repo.obterConfiguracao()
        val perfil = repo.obterPerfilCusto()
        repo.salvarVeiculo(
            v.copy(
                nome = cfg.veiculoNome?.takeIf { it.isNotBlank() } ?: v.nome,
                consumoKmLx100 = cfg.consumoKmLx100,
                precoLitroCentavos = cfg.precoLitroCentavos,
                custoKmCentavos = cfg.custoKmCentavos,
                tarifaMinimaCentavos = cfg.tarifaMinimaCentavos,
                tipo = perfil?.tipoVeiculo ?: v.tipo,
                combustivel = perfil?.combustivel ?: v.combustivel
            )
        )
    }

    suspend fun adicionar(v: Veiculo): Long = repo.salvarVeiculo(v.copy(id = 0))

    /** Depois de restaurar ou zerar: se o ativo não existe mais, volta para o veículo 1. */
    suspend fun garantirAtivoValido() {
        val ids = repo.veiculos().first().map { it.id }
        if (ids.isNotEmpty() && app.preferencias.veiculoAtivo.value !in ids) {
            app.preferencias.definirVeiculoAtivo(Veiculo.PRIMEIRO)
            app.odometro.avisarTroca()
        }
    }
}
