package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.data.repository.emReais
import com.motoristapro.horarios.Horario
import com.motoristapro.horarios.MelhoresHorarios
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import java.time.LocalDate
import java.time.ZoneId

/** Quanto tempo para trás a tela olha. */
private enum class JanelaTempo(val rotulo: String, val dias: Long) {
    SEMANA("Semana", 7),
    MES("Mês", 30),
    TRIMESTRE("90 dias", 90)
}

/** Letras da grade, na ordem do strftime('%w'): domingo primeiro. */
private val LETRAS_DIA = listOf("D", "S", "T", "Q", "Q", "S", "S")

/**
 * "Melhores horários": em que dia e hora as SUAS corridas pagaram mais.
 *
 * Usa as corridas registradas (com o tempo de cada uma), e não as ofertas: a
 * oferta mostra o que o app ofereceu, a corrida mostra o que de fato entrou.
 * R$/h aqui é por hora DE CORRIDA — é a régua que o semáforo também usa.
 */
@Composable
fun MelhoresHorariosScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as MotoristaApp).repository }
    var janela by rememberSaveable { mutableStateOf(JanelaTempo.MES) }

    val hoje = LocalDate.now()
    val zona = ZoneId.systemDefault()
    val inicio = hoje.minusDays(janela.dias - 1).atStartOfDay(zona).toInstant().toEpochMilli()
    val fim = hoje.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()

    val fluxo = remember(inicio, fim) { repo.fatiasHorariasDasCorridas(inicio, fim) }
    val fatias by fluxo.collectAsStateWithLifecycle(initialValue = emptyList())
    val horarios = remember(fatias) { MelhoresHorarios.somar(fatias) }
    val podio = remember(horarios) { MelhoresHorarios.podio(horarios) }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(4.dp)
    ) {
        JanelaTempo.entries.forEach { j ->
            val ativo = j == janela
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (ativo) MaterialTheme.colorScheme.background else SuperficieAlta)
                    .clickable { janela = j }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    j.rotulo, color = if (ativo) Lima else TextoSecundario,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (podio.isEmpty()) {
        Cartao {
            Text("Ainda poucas corridas neste período", fontWeight = FontWeight.Bold)
            Text(
                "Cada horário precisa de pelo menos ${MelhoresHorarios.MINIMO_CORRIDAS} corridas registradas " +
                    "para entrar aqui. Com umas 2 semanas de corridas, os seus melhores horários aparecem sozinhos.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        return
    }

    Rotulo("Seus ${podio.size} melhores horários", Modifier.padding(start = 4.dp, top = 4.dp))
    val maior = podio.first().porHoraCentavos.coerceAtLeast(1)
    podio.forEachIndexed { i, h -> LinhaPodio(i + 1, h, h.porHoraCentavos.toFloat() / maior) }

    Rotulo("Mapa da semana (R$/h)", Modifier.padding(start = 4.dp, top = 8.dp))
    GradeSemana(horarios)

    podio.first().porApp.takeIf { it.size > 1 }?.let { apps ->
        Rotulo("Por app no seu melhor horário", Modifier.padding(start = 4.dp, top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.take(4).forEach { (app, porHora) ->
                Text(
                    "$app ${porHora.emReaisInteiro()}/h",
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(SuperficieAlta)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = TextoSecundario
                )
            }
        }
    }

    Text(
        "R$/h por hora de corrida (com passageiro). Quanto mais corridas você registra, mais certo fica.",
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Lima.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )
}

@Composable
private fun LinhaPodio(posicao: Int, h: Horario, fracao: Float) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Lima.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Text("$posicao", color = Lima, fontWeight = FontWeight.ExtraBold) }
        Column(Modifier.weight(1f)) {
            Text(h.rotulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                "${h.corridas} corridas · ${h.porKmCentavos.emReais()}/km",
                style = MaterialTheme.typography.labelSmall, color = TextoSecundario
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${h.porHoraCentavos.emReaisInteiro()}/h", style = EstiloValor, fontSize = 16.sp)
            Box(Modifier.padding(top = 4.dp).width(70.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(SuperficieAlta)) {
                Box(Modifier.fillMaxWidth(fracao.coerceIn(0.05f, 1f)).height(6.dp).background(Lima))
            }
        }
    }
}

/** 12 faixas de 2 h × 7 dias; quanto mais claro, mais R$/h. Vazio = sem corrida. */
@Composable
private fun GradeSemana(horarios: List<Horario>) {
    val (grade, maior) = remember(horarios) { MelhoresHorarios.grade(horarios) }
    Cartao(espaco = 3) {
        Row {
            Box(Modifier.width(30.dp))
            LETRAS_DIA.forEach { l ->
                Text(
                    l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = TextoSecundario, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        // De 04h a 02h da madrugada: a ordem em que o motorista pensa o dia.
        val ordem = (2..11) + (0..1)
        ordem.forEach { faixa ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02dh".format(faixa * 2), Modifier.width(30.dp),
                    style = MaterialTheme.typography.labelSmall, color = TextoSecundario, fontSize = 9.sp
                )
                (0..6).forEach { dia ->
                    val v = grade[faixa][dia]
                    val cor = if (v <= 0 || maior <= 0) SuperficieAlta
                    else lerp(Lima.copy(alpha = 0.18f), Lima, (v.toFloat() / maior).coerceIn(0f, 1f))
                    Box(Modifier.weight(1f).padding(horizontal = 1.5.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(cor))
                }
            }
        }
    }
}

/** "R$ 41" — reais inteiros: centavo de R$/h não ajuda ninguém a decidir. */
private fun Long.emReaisInteiro(): String = "R$ ${this / 100}"
