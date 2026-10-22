package com.motoristapro.ui.mais

import com.motoristapro.ui.theme.VerdeLucro
import com.motoristapro.ui.componentes.CorSobreDestaque
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.custo.ContaMetaHora
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.custo.QuadroMeta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.TextoSecundario
import java.util.Locale

private val PT_BR = Locale("pt", "BR")

/**
 * Menu ▸ Suas metas: as metas de sempre e, junto, a meta por hora.
 *
 * A pergunta do motorista é "trabalhando X horas, quanto a corrida tem que
 * pagar por hora?". A tela responde com a conta aberta, passo a passo, para
 * ele ver de onde sai o número antes de jogar no semáforo.
 */
@Composable
fun MetaPorHoraConteudo(
    cfg: Configuracao,
    horasPorDia: Int,
    conta: ContaMetaHora?,
    horaBoaAtual: Float,
    /** "moto" ou "carro", conforme o assistente de custo. */
    veiculo: String,
    onHoras: (Int) -> Unit,
    onUsarNoSemaforo: () -> Unit,
    onEditarMeta: () -> Unit
) {
    Cartao(espaco = 10) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo("Suas metas de lucro", Modifier.weight(1f))
            Text(
                "Editar", color = Lima, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEditarMeta() }.padding(4.dp)
            )
        }
        if (cfg.metaLucroMensalCentavos <= 0 || conta == null) {
            Text(
                "Sem meta cadastrada. Toque em Editar: o assistente pergunta quanto você quer " +
                    "levar por mês e daqui sai a meta por hora.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@Cartao
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuadroMeta("Por dia", cfg.metaLucroDiarioCentavos, TextoPrincipal, Modifier.weight(1f))
            QuadroMeta("Semana", cfg.metaLucroSemanalCentavos, TextoPrincipal, Modifier.weight(1f))
            QuadroMeta("Mês", cfg.metaLucroMensalCentavos, TextoPrincipal, Modifier.weight(1f))
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Lima.copy(alpha = 0.12f))
                .border(2.dp, Lima, RoundedCornerShape(12.dp))
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("POR HORA", fontSize = 9.sp, letterSpacing = 1.sp, color = TextoSecundario, fontWeight = FontWeight.SemiBold)
                Text(conta.lucroPorHoraCentavos.emReais(), style = EstiloValor, fontSize = 19.sp, color = VerdeLucro)
            }
        }
        Text(
            "${cfg.diasTrabalhoMes} dias por mês · lucro limpo, já tirados os custos",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }

    Cartao(espaco = 10) {
        Rotulo("Horas por dia")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (2..14).forEach { h ->
                val ativo = h == horasPorDia
                Box(
                    Modifier.size(width = 48.dp, height = 40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (ativo) Lima else SuperficieAlta)
                        .clickable { onHoras(h) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${h}h", fontWeight = FontWeight.Bold,
                        color = if (ativo) CorSobreDestaque else TextoSecundario
                    )
                }
            }
        }
    }

    if (conta == null) return

    Cartao(espaco = 6) {
        Rotulo("Como chega no valor da oferta")
        LinhaConta("Meta do dia ÷ ${conta.horasPorDia} horas", "${conta.lucroPorHoraCentavos.emReais()}/h")
        LinhaConta(
            String.format(
                PT_BR, "+ custo %s %s (%.0f km/h × %s)",
                if (veiculo == "carro") "do" else "da", veiculo, conta.kmPorHora, cfg.custoKmCentavos.emReais()
            ),
            "${conta.custoPorHoraCentavos.emReais()}/h"
        )
        LinhaConta("= faturar por hora trabalhada", "${conta.faturarPorHoraCentavos.emReais()}/h")
        LinhaConta(
            "÷ tempo com passageiro" + if (conta.fracaoEstimada) " (estimado)" else " (suas corridas)",
            "${Math.round(conta.fracaoComPassageiro * 100)}%"
        )
        // Por que a oferta pede mais que o "faturar por hora": o R$/hora do
        // cartão conta só os minutos da corrida, e a espera também tem que
        // ser paga por ela. Sem esta linha, o número parecia errado.
        val pct = Math.round(conta.fracaoComPassageiro * 100)
        val minutos = Math.round(conta.fracaoComPassageiro * 60)
        val faturar = conta.faturarPorHoraCentavos.emReais()
        Text(
            "O R$/hora da oferta conta só o tempo da corrida. Em cada hora você fica uns " +
                "$minutos min com passageiro, e esses $minutos min têm que pagar os $faturar da hora " +
                "inteira: $faturar ÷ $pct% = R$ ${conta.ofertaPorHoraReais} por hora de corrida.",
            style = MaterialTheme.typography.labelSmall, color = TextoSecundario
        )
        HorizontalDivider(color = Contorno, modifier = Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Oferta precisa pagar (por hora de corrida)", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
            )
            Text(
                "R$ ${conta.ofertaPorHoraReais}/h",
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = Lima
            )
        }
    }

    val jaUsando = Math.round(horaBoaAtual) == conta.ofertaPorHoraReais
    Button(
        onClick = onUsarNoSemaforo,
        enabled = !jaUsando,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
    ) {
        Text(
            if (jaUsando) "Semáforo já está na sua meta" else "Usar R$ ${conta.ofertaPorHoraReais}/h no semáforo",
            fontWeight = FontWeight.ExtraBold
        )
    }

    Text(
        buildList {
            if (conta.fracaoEstimada) add(
                "O tempo com passageiro está estimado em 60%. Rodando com o controle de jornada " +
                    "ligado, o app passa a medir pelas suas corridas."
            ) else add(
                "Os ${Math.round(conta.fracaoComPassageiro * 100)}% saem dos seus turnos dos últimos 30 dias: " +
                    "o resto do tempo é esperando chamada e indo buscar o passageiro."
            )
            if (conta.kmPorHoraEstimado) add("Km por hora estimado (15 km/h); preencha o km por dia no assistente ou rode com o GPS da jornada.")
            if (cfg.custoKmCentavos <= 0) add("Sem custo por km cadastrado: a conta usa só o lucro.")
        }.joinToString(" "),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Lima.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )
}

@Composable
private fun LinhaConta(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
