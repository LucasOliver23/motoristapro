package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.comparar.ComparacaoApps
import com.motoristapro.comparar.ResultadoApp
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.LogoPlataforma
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import java.time.LocalDate
import java.time.ZoneId

private enum class PeriodoComparar(val rotulo: String, val dias: Long) {
    SEMANA("Semana", 7), MES("Mês", 30), TRIMESTRE("90 dias", 90)
}

/**
 * "Comparar apps": qual plataforma paga mais para ESTE motorista, nas corridas
 * dele — por hora de corrida, por km e no lucro (valor menos o custo do
 * veículo nos km rodados). E quanto das ofertas de cada app ele aceitou.
 */
@Composable
fun CompararAppsScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as MotoristaApp).repository }
    var periodo by rememberSaveable { mutableStateOf(PeriodoComparar.MES) }

    val zona = ZoneId.systemDefault()
    val hoje = LocalDate.now()
    val inicio = hoje.minusDays(periodo.dias - 1).atStartOfDay(zona).toInstant().toEpochMilli()
    val fim = hoje.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
    val fluxo = remember(inicio, fim) { repo.comparacaoApps(inicio, fim) }
    val apps by fluxo.collectAsStateWithLifecycle(initialValue = emptyList())

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(4.dp)) {
        PeriodoComparar.entries.forEach { p ->
            val ativo = p == periodo
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (ativo) MaterialTheme.colorScheme.background else SuperficieAlta)
                    .clickable { periodo = p }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(p.rotulo, color = if (ativo) Lima else TextoSecundario, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (apps.isEmpty()) {
        Cartao {
            Text("Nenhuma corrida neste período", fontWeight = FontWeight.Bold)
            Text(
                "A comparação usa as corridas que você registra. Registre pelo cartão da oferta ou em Corridas e os apps aparecem aqui.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        return
    }

    apps.forEach { LinhaApp(it) }

    val com99 = apps.firstOrNull { it.aceitePercent != null }
    Text(
        buildString {
            append("Lucro = valor das corridas menos o custo do veículo nos km rodados. ")
            append("O selo de melhor R\$/h só vale para app com ${ComparacaoApps.MINIMO_CORRIDAS}+ corridas.")
            com99?.let { append(" A ${it.plataforma} te mandou ${it.ofertas} ofertas e você aceitou ${it.aceitePercent}%.") }
        },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Lima.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )
}

@Composable
private fun LinhaApp(a: ResultadoApp) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(Superficie)
            .border(2.dp, if (a.melhorPorHora) Lima else Color.Transparent, forma)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoPlataforma(a.plataforma, tamanho = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(a.plataforma, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${a.corridas} corrida(s)" + (a.aceitePercent?.let { " · aceitou $it% de ${a.ofertas} ofertas" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = TextoSecundario
                )
            }
            if (a.melhorPorHora) {
                Text(
                    "MELHOR R\$/h", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Turquesa,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Turquesa.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Numero("R\$/hora", if (a.porHoraCentavos > 0) "R$ ${a.porHoraCentavos / 100}" else "—")
            Numero("R\$/km", if (a.porKmCentavos > 0) a.porKmCentavos.emReais() else "—")
            Numero("Lucro", a.lucroCentavos.emReais())
        }
    }
}

@Composable
private fun Numero(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
