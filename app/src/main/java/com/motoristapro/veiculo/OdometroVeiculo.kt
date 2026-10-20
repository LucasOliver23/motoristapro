package com.motoristapro.veiculo

import android.content.Context
import com.motoristapro.data.repository.FinanceiroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * O hodômetro do app: a última leitura do painel (início/fim do dia, correção
 * à mão ou abastecimento, a que for mais nova) mais o GPS desde então.
 *
 * A leitura fica nas preferências, e não no banco: é um número só, e assim
 * não precisa de migração.
 */
class OdometroVeiculo(context: Context, private val repo: FinanceiroRepository) {

    private val prefs = context.applicationContext.getSharedPreferences("odometro", Context.MODE_PRIVATE)

    private val _mudou = MutableStateFlow(0L)

    /** Muda a cada leitura salva: quem mostra o km observa para reler. */
    val mudou: StateFlow<Long> = _mudou

    /** O veículo 1 usa as chaves de antes da v14 ("km"); os outros, "km_7". */
    private fun sufixo(veiculoId: Long) = if (veiculoId == 1L) "" else "_$veiculoId"

    private fun ultimaLeitura(veiculoId: Long): LeituraOdometro? {
        val sx = sufixo(veiculoId)
        val km = prefs.getLong(KEY_KM + sx, 0)
        if (km <= 0) return null
        val origem = runCatching { OrigemOdometro.valueOf(prefs.getString(KEY_ORIGEM + sx, null) ?: "") }
            .getOrDefault(OrigemOdometro.MANUAL)
        return LeituraOdometro(km, prefs.getLong(KEY_EM + sx, 0), origem)
    }

    /** O km do início da jornada aberta (para o "rodou hoje" ao encerrar). */
    fun leituraDoInicio(veiculoId: Long = repo.veiculoAtivo.value): LeituraOdometro? =
        ultimaLeitura(veiculoId)?.takeIf { it.origem == OrigemOdometro.INICIO }

    /** O km de agora do veículo (o ativo, se não disser qual). */
    suspend fun atual(veiculoId: Long = repo.veiculoAtivo.value): OdometroAtual? {
        val abastecimento = runCatching { repo.ultimoAbastecimentoComOdometro(veiculoId) }.getOrNull()
            ?.let { d -> d.odometroKm?.takeIf { it > 0 }?.let { LeituraOdometro(it, d.dataEm, OrigemOdometro.ABASTECIMENTO) } }
        val base = CalculoOdometro.maisRecente(ultimaLeitura(veiculoId), abastecimento) ?: return null
        val metros = runCatching { repo.metrosGpsDesde(base.em, veiculoId) }.getOrDefault(0L)
        return CalculoOdometro.atual(base, metros)
    }

    fun registrar(
        km: Long,
        origem: OrigemOdometro,
        em: Long = System.currentTimeMillis(),
        veiculoId: Long = repo.veiculoAtivo.value
    ) {
        if (km <= 0) return
        val sx = sufixo(veiculoId)
        prefs.edit()
            .putLong(KEY_KM + sx, km)
            .putLong(KEY_EM + sx, em)
            .putString(KEY_ORIGEM + sx, origem.name)
            .apply()
        _mudou.value = em
    }

    /** Trocou o veículo ativo: quem mostra o km relê. */
    fun avisarTroca() {
        _mudou.value = System.currentTimeMillis()
    }

    private companion object {
        const val KEY_KM = "km"
        const val KEY_EM = "em"
        const val KEY_ORIGEM = "origem"
    }
}
