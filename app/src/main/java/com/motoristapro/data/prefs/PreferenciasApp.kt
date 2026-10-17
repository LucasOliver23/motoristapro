package com.motoristapro.data.prefs

import android.content.Context
import com.motoristapro.pacote.ConfigPacote
import com.motoristapro.pacote.PlanoPacote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Liga/desliga recursos do app (SharedPreferences: leitura instantânea, sem query). */
class PreferenciasApp(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("preferencias_app", Context.MODE_PRIVATE)

    /** Bolha flutuante com o lucro do dia e o cronômetro do turno. */
    var bolhaAtiva: Boolean
        get() = prefs.getBoolean(KEY_BOLHA, true)
        set(v) = prefs.edit().putBoolean(KEY_BOLHA, v).apply()

    /** Notificação com o resumo do dia às 22h. */
    var resumoDiarioAtivo: Boolean
        get() = prefs.getBoolean(KEY_RESUMO, true)
        set(v) = prefs.edit().putBoolean(KEY_RESUMO, v).apply()

    /** Leitura pela imagem (OCR) para apps que escondem o texto da oferta (ex.: 99). */
    var ocrAtivo: Boolean
        get() = prefs.getBoolean(KEY_OCR, true)
        set(v) = prefs.edit().putBoolean(KEY_OCR, v).apply()

    // ------------------------------------------------------------ Copiloto (4.8)

    private val _leitorPausado = MutableStateFlow(prefs.getBoolean(KEY_PAUSADO, false))

    /** O motorista desligou o leitor no Copiloto (o serviço fica ligado, mas não lê). */
    val leitorPausado: StateFlow<Boolean> = _leitorPausado

    fun definirLeitorPausado(v: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSADO, v).apply()
        _leitorPausado.value = v
    }

    private val _appsDesligados = MutableStateFlow(prefs.getStringSet(KEY_APPS_OFF, emptySet())!!.toSet())

    /** Pacotes dos apps que o leitor NÃO deve ler (os logos apagados no Copiloto). */
    val appsDesligados: StateFlow<Set<String>> = _appsDesligados

    fun alternarApp(pacote: String) {
        val novo = _appsDesligados.value.let { if (pacote in it) it - pacote else it + pacote }
        prefs.edit().putStringSet(KEY_APPS_OFF, novo).apply()
        _appsDesligados.value = novo
    }

    private val _notificacaoOferta = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_OFERTA, true))

    /** Notificação com a oferta, endereços e atalhos do mapa. */
    val notificacaoOferta: StateFlow<Boolean> = _notificacaoOferta

    fun definirNotificacaoOferta(v: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_OFERTA, v).apply()
        _notificacaoOferta.value = v
    }

    private val _bolha = MutableStateFlow(bolhaAtiva)

    /** O mesmo [bolhaAtiva], observável (o Copiloto e o Menu ficam iguais). */
    val bolhaFlow: StateFlow<Boolean> = _bolha

    fun definirBolha(v: Boolean) {
        bolhaAtiva = v
        _bolha.value = v
    }

    private val _pacote = MutableStateFlow(
        ConfigPacote(
            plano = PlanoPacote.deNome(prefs.getString(KEY_PACOTE_PLANO, null)),
            lancarSozinho = prefs.getBoolean(KEY_PACOTE_SOZINHO, true)
        )
    )

    /** Pacote 0% de taxa da 99. Flow para o Início e Finanças mudarem na hora em que o motorista troca. */
    val pacote: StateFlow<ConfigPacote> = _pacote

    fun salvarPacote(c: ConfigPacote) {
        prefs.edit()
            .putString(KEY_PACOTE_PLANO, c.plano.name)
            .putBoolean(KEY_PACOTE_SOZINHO, c.lancarSozinho)
            .apply()
        _pacote.value = c
    }

    private companion object {
        const val KEY_PACOTE_PLANO = "pacote99_plano"
        const val KEY_PAUSADO = "leitor_pausado"
        const val KEY_APPS_OFF = "apps_desligados"
        const val KEY_NOTIF_OFERTA = "notificacao_oferta"
        const val KEY_PACOTE_SOZINHO = "pacote99_lancar_sozinho"
        const val KEY_OCR = "ocr_ativo"
        const val KEY_BOLHA = "bolha_ativa"
        const val KEY_RESUMO = "resumo_diario_ativo"
    }
}
