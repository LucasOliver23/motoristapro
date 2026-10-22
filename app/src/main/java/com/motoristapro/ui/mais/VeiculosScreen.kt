package com.motoristapro.ui.mais

import com.motoristapro.ui.componentes.CorSobreDestaque
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.data.local.entity.Veiculo
import com.motoristapro.data.repository.Periodo
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.manutencao.kmPainel
import com.motoristapro.ui.manutencao.maisUrgente
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import com.motoristapro.ui.theme.VermelhoPrejuizo
import com.motoristapro.veiculo.FotoVeiculo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Menu ▸ Meus veículos: cada veículo com o próprio km, custo por km,
 * manutenção e lucro do mês. O ATIVO é o que recebe o que for lançado.
 */
@Composable
fun VeiculosScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MotoristaApp
    val repo = app.repository
    val escopo = rememberCoroutineScope()
    val veiculos by remember { repo.veiculos() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val ativo by app.preferencias.veiculoAtivo.collectAsStateWithLifecycle()
    val (ini, fim) = remember { Periodo.mes(YearMonth.now()) }
    val lucros by remember { repo.lucrosPorVeiculo(ini, fim) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val mudou by app.odometro.mudou.collectAsStateWithLifecycle()
    var resumos by remember { mutableStateOf<Map<Long, Pair<Long?, String?>>>(emptyMap()) }
    var editando by remember { mutableStateOf<Veiculo?>(null) }
    var novo by rememberSaveable { mutableStateOf(false) }

    // Km e manutenção mais urgente de cada um (contas que dependem do GPS e do abastecimento).
    LaunchedEffect(veiculos, mudou) {
        resumos = veiculos.associate { v ->
            val km = runCatching { app.odometro.atual(v.id)?.km }.getOrNull()
            val itens = runCatching { repo.manutencoesDo(v.id).first() }.getOrDefault(emptyList())
            val urgente = if (km != null) maisUrgente(itens)?.let { item ->
                val faltam = item.faltamKm(km)
                if (faltam <= 0) "${item.nome} venceu" else "${item.nome} em ${faltam.kmPainel()} km"
            } else null
            v.id to (km to urgente)
        }
    }

    veiculos.forEach { v ->
        val (km, urgente) = resumos[v.id] ?: (null to null)
        CartaoDoVeiculo(
            v = v,
            ativo = v.id == ativo,
            km = km,
            urgente = urgente,
            lucroMes = lucros.firstOrNull { it.veiculoId == v.id }?.lucroCentavos,
            onUsar = {
                escopo.launch {
                    app.veiculos.ativar(v.id)
                    Toast.makeText(context, "Agora rodando com ${v.nome}", Toast.LENGTH_SHORT).show()
                }
            },
            onEditar = { editando = v }
        )
    }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.5.dp, Contorno, RoundedCornerShape(16.dp))
            .clickable { novo = true }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) { Text("+ Adicionar veículo", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium) }

    Text(
        "Corridas, despesas, jornadas e manutenção ficam marcadas com o veículo ativo. Ao iniciar a jornada, " +
            "o app pergunta com qual você vai rodar. A foto e o km de cada um ficam em Menu ▸ Manutenção.",
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Lima.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )

    if (novo || editando != null) {
        VeiculoDialog(
            inicial = editando,
            podeApagar = editando?.let { it.id != Veiculo.PRIMEIRO && it.id != ativo } == true,
            onDismiss = { novo = false; editando = null },
            onSalvar = { v ->
                novo = false; editando = null
                escopo.launch {
                    runCatching {
                        if (v.id == 0L) app.veiculos.adicionar(v) else repo.salvarVeiculo(v)
                        // Editou o ativo: a configuração (semáforo, lucro) acompanha.
                        if (v.id == ativo) {
                            val cfg = repo.obterConfiguracao()
                            repo.salvarConfiguracao(
                                cfg.copy(veiculoNome = v.nome, custoKmCentavos = v.custoKmCentavos, consumoKmLx100 = v.consumoKmLx100)
                            )
                        }
                    }.onFailure { Toast.makeText(context, it.message ?: "Não deu para salvar", Toast.LENGTH_LONG).show() }
                }
            },
            onApagar = { id ->
                editando = null
                escopo.launch {
                    runCatching { repo.excluirVeiculo(id); FotoVeiculo.remover(context, id) }
                        .onFailure { Toast.makeText(context, it.message ?: "Não deu para apagar", Toast.LENGTH_LONG).show() }
                }
            }
        )
    }
}

@Composable
private fun CartaoDoVeiculo(
    v: Veiculo,
    ativo: Boolean,
    km: Long?,
    urgente: String?,
    lucroMes: Long?,
    onUsar: () -> Unit,
    onEditar: () -> Unit
) {
    val context = LocalContext.current
    val foto = remember(v.id) { FotoVeiculo.carregar(context, v.id)?.asImageBitmap() }
    val forma = RoundedCornerShape(16.dp)
    val tipo = TipoVeiculo.de(v.tipo) ?: TipoVeiculo.MOTO
    Row(
        Modifier.fillMaxWidth().clip(forma).background(Superficie)
            .border(2.dp, if (ativo) Lima else Color.Transparent, forma)
            .clickable { onEditar() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(width = 64.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).background(Lima.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (foto != null) Image(foto, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            else Icon(Icones.doVeiculo(tipo), null, tint = Lima, modifier = Modifier.size(34.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(v.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (ativo) {
                    Text(
                        "ATIVO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Turquesa,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Turquesa.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                listOfNotNull(
                    tipo.rotulo,
                    km?.let { "${it.kmPainel()} km" },
                    v.custoKmCentavos.takeIf { it > 0 }?.let { "${it.emReais()}/km" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = TextoSecundario
            )
            urgente?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = if ("venceu" in it) VermelhoPrejuizo else AmareloAlerta)
            }
            lucroMes?.takeIf { it != 0L }?.let {
                Text("Lucro do mês: ${it.emReais()}", style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
            }
        }
        if (!ativo) {
            Text(
                "Usar", color = Lima, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUsar() }.padding(6.dp)
            )
        }
    }
}

@Composable
private fun VeiculoDialog(
    inicial: Veiculo?,
    podeApagar: Boolean,
    onDismiss: () -> Unit,
    onSalvar: (Veiculo) -> Unit,
    onApagar: (Long) -> Unit
) {
    var nome by rememberSaveable { mutableStateOf(inicial?.nome.orEmpty()) }
    var tipo by rememberSaveable { mutableStateOf(TipoVeiculo.de(inicial?.tipo) ?: TipoVeiculo.MOTO) }
    var custo by rememberSaveable {
        mutableStateOf(inicial?.custoKmCentavos?.takeIf { it > 0 }?.let { String.format(java.util.Locale("pt", "BR"), "%.2f", it / 100.0) }.orEmpty())
    }
    var consumo by rememberSaveable {
        mutableStateOf(inicial?.consumoKmLx100?.takeIf { it > 0 }?.let { String.format(java.util.Locale("pt", "BR"), "%.1f", it / 100.0) }.orEmpty())
    }
    val custoCentavos = custo.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    val consumoX100 = consumo.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (inicial == null) "Novo veículo" else "Editar veículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoVeiculo.entries.forEach { t ->
                        val ativo = tipo == t
                        Text(
                            t.rotulo,
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (ativo) Lima else SuperficieAlta)
                                .clickable { tipo = t }.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = if (ativo) CorSobreDestaque else TextoSecundario,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                CampoFormulario("Nome (ex.: Onix 1.0 2020)", nome, { nome = it }, numerico = false)
                CampoFormulario(
                    "Custo por km (R$)", custo, { custo = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    ajuda = "Se não souber, deixe vazio e use o assistente de custo com este veículo ativo"
                )
                if (tipo.usaCombustivel) {
                    CampoFormulario("Consumo (km/L)", consumo, { consumo = it.filter { c -> c.isDigit() || c == ',' || c == '.' } })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = {
                val base = inicial ?: Veiculo(nome = "")
                onSalvar(
                    base.copy(
                        nome = nome.trim(),
                        tipo = tipo.name,
                        custoKmCentavos = custoCentavos,
                        consumoKmLx100 = if (tipo.usaCombustivel) consumoX100 else 0
                    )
                )
            }) { Text("Salvar", color = Lima) }
        },
        dismissButton = {
            Row {
                if (podeApagar && inicial != null) {
                    TextButton(onClick = { onApagar(inicial.id) }) { Text("Apagar", color = VermelhoPrejuizo) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
