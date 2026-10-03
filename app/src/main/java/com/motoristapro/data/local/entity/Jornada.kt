package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Um turno de trabalho (cronômetro de horas + km medido pelo GPS).
 * fim_em == null significa jornada em andamento (só existe uma por vez).
 */
@Entity(
    tableName = "jornadas",
    indices = [Index(value = ["inicio_em"])]
)
data class Jornada(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "inicio_em")
    val inicioEm: Long,

    @ColumnInfo(name = "fim_em")
    val fimEm: Long? = null,

    /** Distância percorrida medida pelo GPS, em metros. */
    @ColumnInfo(name = "metros_gps", defaultValue = "0")
    val metrosGps: Long = 0,

    // ------------------------------------------------------ tempo por estado
    // Guardado em SEGUNDOS acumulados, e não como uma lista de eventos: o que a
    // tela mostra é o total de cada estado, e somar uma linha por transição
    // encheria o banco sem responder nada que estes cinco números não respondam.

    /** Nenhum app de corrida online. */
    @ColumnInfo(name = "seg_offline", defaultValue = "0")
    val segOffline: Long = 0,

    /** Pelo menos um app online, sem corrida em andamento. */
    @ColumnInfo(name = "seg_aguardando", defaultValue = "0")
    val segAguardando: Long = 0,

    /** A caminho do passageiro (ainda não detectado automaticamente). */
    @ColumnInfo(name = "seg_buscando", defaultValue = "0")
    val segBuscando: Long = 0,

    /** Parado esperando o passageiro entrar (ainda não detectado automaticamente). */
    @ColumnInfo(name = "seg_esperando", defaultValue = "0")
    val segEsperando: Long = 0,

    /** Com o passageiro a bordo (ainda não detectado automaticamente). */
    @ColumnInfo(name = "seg_em_viagem", defaultValue = "0")
    val segEmViagem: Long = 0,

    // ------------------------------------------------- tempo online por app
    @ColumnInfo(name = "seg_uber", defaultValue = "0")
    val segUber: Long = 0,

    @ColumnInfo(name = "seg_99", defaultValue = "0")
    val seg99: Long = 0,

    @ColumnInfo(name = "seg_ifood", defaultValue = "0")
    val segIfood: Long = 0,

    @ColumnInfo(name = "seg_indrive", defaultValue = "0")
    val segInDrive: Long = 0,

    /** Jornada pausada à mão: o relógio para sem encerrar o turno. 0 = rodando. */
    @ColumnInfo(name = "pausada_em", defaultValue = "0")
    val pausadaEm: Long = 0,

    /**
     * Quanto tempo o turno ficou pausado, em segundos.
     *
     * Sem isto, o cronômetro seria "agora menos o início" e contaria o almoço
     * junto. Cada vez que o motorista retoma, soma-se aqui o tempo que ficou
     * parado — e o relógio volta a bater com a realidade mesmo depois de três
     * ou quatro pausas no mesmo turno.
     */
    @ColumnInfo(name = "seg_pausados", defaultValue = "0")
    val segPausados: Long = 0
) {
    val ativa: Boolean get() = fimEm == null
    val pausada: Boolean get() = pausadaEm > 0

    /**
     * O cronômetro do turno, já descontando as pausas.
     *
     * Pausada, congela no instante da pausa. Encerrada, vale até o fim.
     */
    fun segundosRodando(agora: Long): Long {
        val ate = when {
            fimEm != null -> fimEm
            pausadaEm > 0 -> pausadaEm
            else -> agora
        }
        return ((ate - inicioEm) / 1000 - segPausados).coerceAtLeast(0)
    }

    /** Soma dos cinco estados. É o "Tempo total" da tela. */
    val segundosContados: Long
        get() = segOffline + segAguardando + segBuscando + segEsperando + segEmViagem
}
