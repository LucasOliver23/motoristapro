package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.MotoristaApp
import com.motoristapro.data.repository.emReais
import com.motoristapro.pacote.ConfigPacote
import com.motoristapro.pacote.PlanoPacote
import com.motoristapro.ui.componentes.LogoPlataforma
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario

/**
 * Menu ▸ Pacote 0% de taxa: qual pacote da 99 o motorista paga.
 *
 * Os planos são os mesmos da tela da 99. Com "lançar sozinho", a primeira
 * corrida da 99 de cada período lança o preço do pacote como despesa
 * "Taxa do app" — e o lucro do dia já sai certo.
 */
@Composable
fun PacoteTaxaScreen(onSalvo: () -> Unit) {
    val app = LocalContext.current.applicationContext as MotoristaApp
    val atual by app.preferencias.pacote.collectAsState()
    var plano by remember { mutableStateOf(atual.plano) }
    var sozinho by remember { mutableStateOf(atual.lancarSozinho) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoPlataforma("99", tamanho = 34.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("99 Motorista", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Com o pacote, corrida de R$ 20 = R$ 20 pra você",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
        }

        Rotulo("Pacote de tempo", Modifier.padding(top = 8.dp))
        listOf(PlanoPacote.DIA, PlanoPacote.TRES_DIAS).forEach { p ->
            OpcaoPlano(p, p == plano) { plano = p }
        }
        Rotulo("Pacote de ganhos", Modifier.padding(top = 8.dp))
        listOf(PlanoPacote.GANHOS_135, PlanoPacote.GANHOS_455).forEach { p ->
            OpcaoPlano(p, p == plano) { plano = p }
        }
        OpcaoPlano(PlanoPacote.NENHUM, plano == PlanoPacote.NENHUM) { plano = PlanoPacote.NENHUM }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lançar sozinho", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Desconta o pacote no dia em que você rodar na 99",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
            Switch(
                checked = sozinho,
                onCheckedChange = { sozinho = it },
                enabled = plano != PlanoPacote.NENHUM,
                colors = SwitchDefaults.colors(checkedTrackColor = Lima, checkedThumbColor = Color(0xFF0D1117))
            )
        }

        Button(
            onClick = {
                app.preferencias.salvarPacote(ConfigPacote(plano, sozinho))
                onSalvo()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0D1117))
        ) { Text("Salvar", fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun OpcaoPlano(plano: PlanoPacote, selecionado: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(forma)
            .background(if (selecionado) Lima.copy(alpha = 0.12f) else SuperficieAlta)
            .border(2.dp, if (selecionado) Lima else Color.Transparent, forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selecionado, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Lima)
        )
        Column(Modifier.weight(1f)) {
            Text(plano.rotulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(plano.detalhe, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        }
        if (plano.precoCentavos > 0) {
            Text(plano.precoCentavos.emReais(), style = EstiloValor, fontSize = 17.sp, modifier = Modifier.padding(end = 10.dp))
        }
    }
}
