package com.motoristapro.ui.dashboard

import com.motoristapro.ui.theme.VerdeLucro
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.formatarDuracao
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.AzulInfo
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Roxo
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.time.LocalDate
import kotlin.random.Random

/**
 * Lembra o último dia em que a comemoração apareceu: ela é uma vez por dia.
 * Sem isso, cada corrida depois da meta abriria a tela de novo.
 */
object ComemoracaoMetaPrefs {
    private const val ARQUIVO = "comemoracao_meta"
    private const val CHAVE = "ultimo_dia"

    fun jaComemorou(c: Context, dia: LocalDate): Boolean =
        c.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE, null) == dia.toString()

    fun marcar(c: Context, dia: LocalDate) {
        c.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit().putString(CHAVE, dia.toString()).apply()
    }
}

/**
 * A tela de "meta do dia batida": confete, o lucro, o R$/hora e a sequência
 * de dias. Vibra uma vez ao abrir — o motorista sente no bolso mesmo sem olhar.
 */
@Composable
fun ComemoracaoMeta(
    lucroCentavos: Long,
    segundosRodados: Long,
    sequencia: SequenciaMeta?,
    turnoAberto: Boolean,
    onContinuar: () -> Unit,
    onEncerrar: () -> Unit
) {
    val haptico = LocalHapticFeedback.current
    val escala = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        haptico.performHapticFeedback(HapticFeedbackType.LongPress)
        escala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Dialog(
        onDismissRequest = onContinuar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Fundo)
                .background(Brush.radialGradient(listOf(VerdeLucro.copy(alpha = 0.18f), Color.Transparent)))
        ) {
            Confete(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.scale(escala.value).size(110.dp).clip(CircleShape)
                        .background(VerdeLucro.copy(alpha = 0.14f)).border(3.dp, VerdeLucro, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icones.Certo, contentDescription = null, tint = VerdeLucro, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Meta do dia batida!",
                    style = EstiloValor, fontSize = 28.sp, color = TextoPrincipal, textAlign = TextAlign.Center
                )
                Text(lucroCentavos.emReais(), style = EstiloValor, fontSize = 48.sp, color = VerdeLucro)
                if (segundosRodados >= 60) {
                    val porHora = lucroCentavos * 3600 / segundosRodados
                    Text(
                        "em ${segundosRodados.formatarDuracao()} de jornada · ${porHora.emReais()}/h",
                        style = MaterialTheme.typography.bodyMedium, color = TextoSecundario,
                        textAlign = TextAlign.Center
                    )
                }

                if (sequencia != null && sequencia.atual > 0) {
                    Spacer(Modifier.height(28.dp))
                    CartaoSequencia(sequencia)
                }

                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onContinuar,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Fundo)
                ) {
                    Text(if (turnoAberto) "Continuar rodando" else "Fechar", fontWeight = FontWeight.ExtraBold)
                }
                if (turnoAberto) {
                    TextButton(onClick = onEncerrar) {
                        Text("Encerrar o dia", color = TextoSecundario, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoSequencia(s: SequenciaMeta) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            com.motoristapro.ui.componentes.IconeTexto(com.motoristapro.ui.componentes.Icones.Fogo, AmareloAlerta, 34.dp)
            Column {
                Text(
                    if (s.atual == 1) "1 dia batendo a meta" else "${s.atual} dias seguidos",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextoPrincipal
                )
                val falta = s.recorde - s.atual
                Text(
                    when {
                        falta > 0 -> "Seu recorde é ${s.recorde}. Faltam $falta!"
                        s.atual > 1 -> "É o seu recorde!"
                        else -> "Amanhã tem mais."
                    },
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            s.semana.forEachIndexed { i, d ->
                val (fundo, texto) = when (d.situacao) {
                    SequenciaMeta.Situacao.BATIDA -> VerdeLucro to Fundo
                    SequenciaMeta.Situacao.NAO_BATIDA -> SuperficieAlta to VermelhoPrejuizo
                    else -> SuperficieAlta to TextoSecundario
                }
                Box(
                    Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(fundo),
                    contentAlignment = Alignment.Center
                ) {
                    Text(LETRAS_SEMANA[i], color = texto, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

private val LETRAS_SEMANA = listOf("S", "T", "Q", "Q", "S", "S", "D")

/** Confete parado, em posições sorteadas uma vez: enfeita sem roubar o olho do número. */
@Composable
private fun Confete(modifier: Modifier) {
    val cores = listOf(Lima, AmareloAlerta, Turquesa, Roxo, AzulInfo, VermelhoPrejuizo)
    val pecas = remember {
        val r = Random(7)
        List(26) {
            Peca(r.nextFloat(), r.nextFloat() * 0.55f, r.nextFloat() * 180f, r.nextInt(cores.size), r.nextBoolean())
        }
    }
    Canvas(modifier) {
        pecas.forEach { p ->
            val centro = Offset(p.x * size.width, p.y * size.height)
            val cor = cores[p.cor].copy(alpha = 0.85f)
            if (p.redonda) {
                drawCircle(cor, radius = 4.dp.toPx(), center = centro)
            } else {
                rotate(p.angulo, pivot = centro) {
                    drawRect(
                        cor,
                        topLeft = centro - Offset(6.dp.toPx(), 2.5.dp.toPx()),
                        size = Size(12.dp.toPx(), 5.dp.toPx())
                    )
                }
            }
        }
    }
}

private class Peca(val x: Float, val y: Float, val angulo: Float, val cor: Int, val redonda: Boolean)
