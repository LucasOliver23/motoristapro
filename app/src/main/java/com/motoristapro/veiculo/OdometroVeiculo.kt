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

    private fun ultimaLeitura(): LeituraOdometro? {
        val km = prefs.getLong(KEY_KM, 0)
        if (km <= 0) return null
        val origem = runCatching { OrigemOdometro.valueOf(prefs.getString(KEY_ORIGEM, null) ?: "") }
            .getOrDefault(OrigemOdometro.MANUAL)
        return LeituraOdometro(km, prefs.getLong(KEY_EM, 0), origem)
    }

    /** O km do início da jornada aberta (para o "rodou hoje" ao encerrar). */
    fun leituraDoInicio(): LeituraOdometro? = ultimaLeitura()?.takeIf { it.origem == OrigemOdometro.INICIO }

    suspend fun atual(): OdometroAtual? {
        val abastecimento = runCatching { repo.ultimoAbastecimentoComOdometro() }.getOrNull()
            ?.let { d -> d.odometroKm?.takeIf { it > 0 }?.let { LeituraOdometro(it, d.dataEm, OrigemOdometro.ABASTECIMENTO) } }
        val base = CalculoOdometro.maisRecente(ultimaLeitura(), abastecimento) ?: return null
        val metros = runCatching { repo.metrosGpsDesde(base.em) }.getOrDefault(0L)
        return CalculoOdometro.atual(base, metros)
    }

    fun registrar(km: Long, origem: OrigemOdometro, em: Long = System.currentTimeMillis()) {
        if (km <= 0) return
        prefs.edit()
            .putLong(KEY_KM, km)
            .putLong(KEY_EM, em)
            .putString(KEY_ORIGEM, origem.name)
            .apply()
        _mudou.value = em
    }

    private companion object {
        const val KEY_KM = "km"
        const val KEY_EM = "em"
        const val KEY_ORIGEM = "origem"
    }
}
