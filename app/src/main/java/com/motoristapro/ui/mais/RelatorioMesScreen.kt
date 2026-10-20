package com.motoristapro.ui.mais

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.data.repository.emReais
import com.motoristapro.relatorio.RelatorioPdf
import com.motoristapro.relatorio.ResumoMes
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val PT_BR = Locale("pt", "BR")

/**
 * Relatório do mês: prévia na tela e o PDF para compartilhar (WhatsApp,
 * e-mail) ou salvar onde o motorista quiser.
 */
@Composable
fun RelatorioMesScreen() {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val meses = remember { (2 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) } }
    var mes by remember { mutableStateOf(meses.last()) }
    var resumo by remember { mutableStateOf<ResumoMes?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    LaunchedEffect(mes) {
        resumo = null
        resumo = runCatching { RelatorioPdf.montar(context, mes) }.getOrNull()
    }

    val salvar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val r = resumo ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        escopo.launch {
            runCatching {
                val arquivo = RelatorioPdf.gerar(context, r)
                context.contentResolver.openOutputStream(uri)?.use { saida -> arquivo.inputStream().use { it.copyTo(saida) } }
            }.onSuccess { Toast.makeText(context, "Relatório salvo", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Não deu para salvar: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(4.dp)) {
        meses.forEach { m ->
            val ativo = m == mes
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (ativo) MaterialTheme.colorScheme.background else SuperficieAlta)
                    .clickable { mes = m }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    m.month.getDisplayName(TextStyle.FULL, PT_BR).replaceFirstChar { it.uppercase() },
                    color = if (ativo) Lima else TextoSecundario, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    val r = resumo
    if (r == null) {
        Text("Montando o relatório…", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        return
    }

    // A prévia imita a folha do PDF: fundo branco, como vai sair impresso.
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val preto = Color(0xFF111111)
        val cinza = Color(0xFF666666)
        Text(
            "MotoristaPro · Relatório de ${r.mes.month.getDisplayName(TextStyle.FULL, PT_BR)}/${r.mes.year}",
            color = preto, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium
        )
        Text(listOfNotNull(r.motorista, r.cidade, r.veiculo).joinToString(" · "), color = cinza, style = MaterialTheme.typography.labelSmall)
        LinhaFolha("Ganhos brutos (${r.corridas} corridas)", r.ganhosCentavos.emReais(), preto)
        if (r.porApp.isNotEmpty()) {
            LinhaFolha(r.porApp.joinToString(" · ") { it.first }, r.porApp.joinToString(" · ") { "${it.second / 100}" }, preto)
        }
        LinhaFolha("Combustível", "− " + r.combustivelCentavos.emReais(), preto)
        LinhaFolha("Manutenção e outros", "− " + r.outrasDespesasCentavos.emReais(), preto)
        LinhaFolha("Custos fixos do mês", "− " + r.custosFixosCentavos.emReais(), preto)
        LinhaFolha("Lucro líquido", r.lucroCentavos.emReais(), Color(0xFF0A7D5A), forte = true)
        Text(
            buildList {
                add(String.format(PT_BR, "%,.0f km", r.metros / 1000.0))
                if (r.segundosJornada > 0) add("${r.segundosJornada / 3600} h de jornada")
                if (r.porKmCentavos > 0) add("${r.porKmCentavos.emReais()}/km")
                if (r.porHoraCentavos > 0) add("${r.porHoraCentavos.emReais()}/h")
            }.joinToString(" · "),
            color = cinza, style = MaterialTheme.typography.labelSmall
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (ocupado) return@Button
                ocupado = true
                escopo.launch {
                    runCatching { RelatorioPdf.compartilhar(context, RelatorioPdf.gerar(context, r)) }
                        .onFailure { Toast.makeText(context, "Não deu para gerar o PDF: ${it.message}", Toast.LENGTH_LONG).show() }
                    ocupado = false
                }
            },
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0D1117))
        ) { Text("Compartilhar PDF", fontWeight = FontWeight.ExtraBold) }
        Button(
            onClick = { salvar.launch("motoristapro-${r.mes}.pdf") },
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuperficieAlta, contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text("Salvar", fontWeight = FontWeight.Bold) }
    }

    Text(
        "O PDF traz também a lista das corridas e despesas do mês, dia por dia.",
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Lima.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )
}

@Composable
private fun LinhaFolha(rotulo: String, valor: String, cor: Color, forte: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(rotulo, Modifier.weight(1f), color = Color(0xFF111111), style = MaterialTheme.typography.bodySmall,
            fontWeight = if (forte) FontWeight.Bold else FontWeight.Normal)
        Text(valor, color = cor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
