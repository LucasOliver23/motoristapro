package com.motoristapro.ui.manutencao

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.veiculo.CalculoLembrete
import com.motoristapro.veiculo.CalculoOdometro
import com.motoristapro.veiculo.FotoVeiculo
import com.motoristapro.veiculo.OdometroAtual
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.BotaoPro
import com.motoristapro.ui.componentes.BotaoProEscuro
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo

/**
 * Como o item aparece: vencido, perto ou tranquilo.
 *
 * O corte de 500 km não é redondo por acaso — é mais ou menos dois dias de
 * trabalho de quem roda de aplicativo. Avisar com menos que isso é avisar
 * quando já não dá tempo de marcar a oficina.
 */
internal const val AVISO_KM = 500L

fun corDaManutencao(faltam: Long): Color = when {
    faltam <= 0 -> VermelhoPrejuizo
    faltam <= AVISO_KM -> AmareloAlerta
    else -> Lima
}

fun textoDaManutencao(item: ItemManutencao, odometroAtual: Long): String {
    if (odometroAtual <= 0) return "informe o km do painel"
    val faltam = item.faltamKm(odometroAtual)
    return when {
        faltam <= 0 -> "venceu há ${-faltam} km"
        else -> "vence em $faltam km"
    }
}

/** O item mais urgente, que é o que o Menu mostra na linha. */
fun maisUrgente(itens: List<ItemManutencao>): ItemManutencao? = itens.minByOrNull { it.odometroDoVencimento }

/** O veículo no alto da tela: o tipo decide o desenho, o resto é o texto. */
data class VeiculoResumo(val tipo: TipoVeiculo, val nome: String?, val detalhe: String, val id: Long = 1L)

/**
 * A tela de manutenção: o veículo, o odômetro, o que venceu e a lista.
 *
 * Tudo em km e nada em data, de propósito: o motorista sabe de cabeça quantos
 * km roda por dia, e é o km que estraga a peça. O km vem do painel (perguntado
 * ao abrir e fechar a jornada) somado ao GPS no meio do dia.
 */
@Composable
fun ManutencaoConteudo(
    itens: List<ItemManutencao>,
    odometro: OdometroAtual?,
    veiculo: VeiculoResumo,
    onSalvar: (ItemManutencao) -> Unit,
    onExcluir: (Long) -> Unit,
    onCorrigirKm: (Long) -> Unit,
    onEditarVeiculo: () -> Unit,
    avisoKm: Int = CalculoLembrete.PADRAO_KM,
    onAvisoKm: (Int) -> Unit = {}
) {
    val odometroAtual = odometro?.km ?: 0L
    var editando by rememberSaveable { mutableStateOf<Long?>(null) }
    var acoes by rememberSaveable { mutableStateOf<Long?>(null) }
    var novo by rememberSaveable { mutableStateOf(false) }
    var excluir by rememberSaveable { mutableStateOf<Long?>(null) }
    var corrigir by rememberSaveable { mutableStateOf(false) }

    fun fizAgora(item: ItemManutencao) =
        onSalvar(item.copy(odometroUltimaKm = odometroAtual, atualizadoEm = System.currentTimeMillis()))

    CartaoVeiculo(veiculo, onEditarVeiculo)
    CartaoOdometro(odometro) { corrigir = true }

    // O pior atrasado vira faixa vermelha com o botão: é o que não pode esperar.
    if (odometroAtual > 0) {
        itens.filter { it.faltamKm(odometroAtual) <= 0 }.minByOrNull { it.faltamKm(odometroAtual) }?.let { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(VermelhoPrejuizo.copy(alpha = 0.12f))
                    .border(1.5.dp, VermelhoPrejuizo, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("⚠", color = AmareloAlerta, fontSize = 18.sp)
                Text(
                    buildAnnotatedString {
                        append("${item.nome} · ")
                        withStyle(SpanStyle(color = VermelhoPrejuizo, fontWeight = FontWeight.Bold)) {
                            append("${(-item.faltamKm(odometroAtual)).kmPainel()} km")
                        }
                        append(" além do prazo")
                    },
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                )
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(VermelhoPrejuizo)
                        .clickable { fizAgora(item) }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Fiz agora", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }

    Text("Itens", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
    if (itens.isEmpty()) {
        Text(
            "Nenhum item ainda. Cadastre o que você controla — óleo, relação, pneu, " +
                "freio, revisão — com o intervalo em km e o km da última troca.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }
    itens.forEach { item -> LinhaItem(item, odometroAtual) { acoes = item.id } }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Contorno, RoundedCornerShape(16.dp))
            .clickable { novo = true }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) { Text("+ Adicionar item", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium) }

    // Quantos km antes o lembrete avisa na barra do celular (e o cartão do Início aparece).
    Cartao(espaco = 6) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Me avisar antes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("quando faltar", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalculoLembrete.OPCOES_KM.forEach { km ->
                    val ativo = km == avisoKm
                    Text(
                        "$km km",
                        modifier = Modifier.clip(RoundedCornerShape(99.dp))
                            .background(if (ativo) Lima else SuperficieAlta)
                            .clickable { onAvisoKm(km) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (ativo) Color(0xFF0D1117) else TextoSecundario,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    itens.firstOrNull { it.id == acoes }?.let { item ->
        AlertDialog(
            onDismissRequest = { acoes = null },
            title = { Text(item.nome) },
            text = {
                Text(
                    "A cada ${item.intervaloKm.kmPainel()} km · última em ${item.odometroUltimaKm.kmPainel()} km. " +
                        textoDaManutencao(item, odometroAtual).replaceFirstChar { it.uppercase() } + ".",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { fizAgora(item); acoes = null }, enabled = odometroAtual > 0) {
                    Text(if (odometroAtual > 0) "Fiz agora (${odometroAtual.kmPainel()} km)" else "Fiz agora", color = Lima)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { excluir = item.id; acoes = null }) { Text("Excluir", color = VermelhoPrejuizo) }
                    TextButton(onClick = { editando = item.id; acoes = null }) { Text("Editar") }
                }
            }
        )
    }

    val alvo = itens.firstOrNull { it.id == editando }
    if (novo || alvo != null) {
        ManutencaoDialog(
            inicial = alvo,
            odometroAtual = odometroAtual,
            onDismiss = { novo = false; editando = null },
            onSalvar = { onSalvar(it); novo = false; editando = null }
        )
    }
    excluir?.let { id ->
        AlertDialog(
            onDismissRequest = { excluir = null },
            title = { Text("Excluir item") },
            text = { Text("O item sai da lista e o aviso para de aparecer.") },
            confirmButton = {
                TextButton(onClick = { onExcluir(id); excluir = null }) {
                    Text("Excluir", color = VermelhoPrejuizo)
                }
            },
            dismissButton = { TextButton(onClick = { excluir = null }) { Text("Cancelar") } }
        )
    }
    if (corrigir) {
        var texto by rememberSaveable { mutableStateOf(odometroAtual.takeIf { it > 0 }?.toString().orEmpty()) }
        val km = texto.toLongOrNull() ?: 0L
        AlertDialog(
            onDismissRequest = { corrigir = false },
            title = { Text("Corrigir odômetro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Digite o km que está no painel agora.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                    CampoFormulario(
                        rotulo = "Hodômetro (km)", valor = texto,
                        onValor = { texto = it.filter(Char::isDigit).take(7) },
                        ajuda = if (texto.isBlank()) null else CalculoOdometro.aviso(km, odometro)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onCorrigirKm(km); corrigir = false }, enabled = km > 0) { Text("Salvar", color = Lima) }
            },
            dismissButton = { TextButton(onClick = { corrigir = false }) { Text("Cancelar") } }
        )
    }
}

/**
 * O veículo: foto que o motorista escolheu na galeria, ou o desenho de moto
 * ou carro enquanto não tem foto.
 */
@Composable
private fun CartaoVeiculo(v: VeiculoResumo, onEditar: () -> Unit) {
    val context = LocalContext.current
    var foto by remember(v.id) { mutableStateOf(FotoVeiculo.carregar(context, v.id)?.asImageBitmap()) }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && FotoVeiculo.salvar(context, uri, v.id)) foto = FotoVeiculo.carregar(context, v.id)?.asImageBitmap()
    }
    Cartao(espaco = 10) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(width = 84.dp, height = 62.dp).clip(RoundedCornerShape(14.dp))
                    .background(Lima.copy(alpha = 0.12f))
                    .clickable { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                val f = foto
                if (f != null) {
                    Image(f, contentDescription = "Foto do veículo", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else {
                    Icon(
                        Icones.doVeiculo(v.tipo),
                        contentDescription = v.tipo.rotulo, tint = Lima, modifier = Modifier.size(42.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    v.nome ?: if (v.tipo == TipoVeiculo.CARRO) "Meu carro" else "Minha ${v.tipo.rotulo.lowercase()}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
                Text(v.detalhe, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (foto == null) "Pôr foto" else "Trocar foto", color = Lima, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
            if (foto != null) {
                Text(
                    "Tirar foto", color = TextoSecundario, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { FotoVeiculo.remover(context, v.id); foto = null }
                )
            }
            Box(Modifier.weight(1f))
            Text(
                "Editar veículo", color = Lima, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onEditar() }
            )
        }
    }
}

@Composable
private fun CartaoOdometro(odometro: OdometroAtual?, onCorrigir: () -> Unit) {
    Cartao(espaco = 4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo("Odômetro atual", Modifier.weight(1f))
            Text(
                if (odometro == null) "Informar" else "Corrigir", color = Lima, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCorrigir() }.padding(4.dp)
            )
        }
        if (odometro == null) {
            Text("—", style = EstiloValor, fontSize = 30.sp, color = TextoSecundario)
            Text(
                "Toque em Informar e digite o km do painel. Depois o app pergunta ao iniciar e " +
                    "ao encerrar a jornada, e o GPS soma no meio do dia.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@Cartao
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(odometro.km.kmPainel(), style = EstiloValor, fontSize = 32.sp)
            Text(" km", style = MaterialTheme.typography.bodyMedium, color = TextoSecundario, modifier = Modifier.padding(bottom = 5.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (odometro.estimado) "GPS" else "PAINEL",
                fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Lima,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Lima.copy(alpha = 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp)
            )
            Text(
                if (odometro.estimado)
                    "${odometro.base.km.kmPainel()} km ${odometro.base.origem.rotulo} + ${odometro.kmGps} km do GPS"
                else odometro.base.origem.rotulo.replaceFirstChar { it.uppercase() } + " · " + quando(odometro.base.em),
                style = MaterialTheme.typography.labelSmall, color = TextoSecundario
            )
        }
    }
}

private fun quando(em: Long): String {
    val dt = Instant.ofEpochMilli(em).atZone(ZoneId.systemDefault())
    val dia = dt.toLocalDate()
    val hoje = LocalDate.now()
    val d = when (dia) {
        hoje -> "hoje"
        hoje.minusDays(1) -> "ontem"
        else -> dt.format(DateTimeFormatter.ofPattern("dd/MM"))
    }
    return d + ", " + dt.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Uma linha da lista: anel do quanto já rodou, nome, faixa de km e o estado. */
@Composable
private fun LinhaItem(item: ItemManutencao, odometroAtual: Long, onClick: () -> Unit) {
    val temKm = odometroAtual > 0
    val faltam = item.faltamKm(odometroAtual)
    val cor = if (temKm) corDaManutencao(faltam) else TextoSecundario
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Superficie)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            val fracao = if (temKm) item.fracaoUsada(odometroAtual) else 0f
            Canvas(Modifier.matchParentSize()) {
                val traco = 4.dp.toPx()
                val canto = Offset(traco / 2, traco / 2)
                val lado = Size(size.width - traco, size.height - traco)
                drawArc(Contorno, -90f, 360f, false, canto, lado, style = Stroke(traco))
                if (fracao > 0f) drawArc(cor, -90f, 360f * fracao, false, canto, lado, style = Stroke(traco, cap = StrokeCap.Round))
            }
            Text(emojiDoItem(item.nome), fontSize = 15.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(item.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                "${item.odometroUltimaKm.kmPainel()} → ${item.odometroDoVencimento.kmPainel()} km",
                style = MaterialTheme.typography.labelSmall, color = TextoSecundario
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (temKm) {
                Text(
                    when {
                        faltam <= 0 -> "ATRASADA"
                        faltam <= AVISO_KM -> "ATENÇÃO"
                        else -> "EM DIA"
                    },
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = cor,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(cor.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Text(
                when {
                    !temKm -> "informe o km"
                    faltam <= 0 -> "${(-faltam).kmPainel()} km além"
                    else -> "faltam ${faltam.kmPainel()} km"
                },
                style = MaterialTheme.typography.labelSmall, color = TextoSecundario,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

private fun emojiDoItem(nome: String): String {
    val n = nome.lowercase()
    return when {
        "óleo" in n || "oleo" in n -> "💧"
        "rela" in n || "corrente" in n -> "⛓"
        "pneu" in n -> "◎"
        "freio" in n || "pastilha" in n || "lona" in n -> "🛑"
        "filtro" in n -> "🌀"
        "bateria" in n -> "🔋"
        else -> "🔧"
    }
}

@Composable
private fun ManutencaoDialog(
    inicial: ItemManutencao?,
    odometroAtual: Long,
    onDismiss: () -> Unit,
    onSalvar: (ItemManutencao) -> Unit
) {
    var nome by rememberSaveable { mutableStateOf(inicial?.nome ?: "") }
    var intervalo by rememberSaveable { mutableStateOf(inicial?.intervaloKm?.toString() ?: "") }
    // Item novo começa VAZIO: vir preenchido com o km de hoje fazia quem
    // trocou o óleo há 600 km salvar sem ver, e a conta saía 600 km errada.
    var ultima by rememberSaveable { mutableStateOf(inicial?.odometroUltimaKm?.toString().orEmpty()) }
    // Dá para dizer a próxima troca em vez da última: é o que vem escrito
    // na etiqueta da oficina ("próxima troca 84.000").
    var pelaProxima by rememberSaveable { mutableStateOf(false) }
    val km = intervalo.trim().toLongOrNull()
    val digitado = ultima.trim().toLongOrNull()
    val odo = if (pelaProxima) digitado?.let { p -> km?.let { p - it } } else digitado
    val valido = nome.isNotBlank() && km != null && km > 0 && odo != null && odo >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (inicial == null) "Novo item" else "Editar item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Sugestões: é o que quase todo mundo controla, e poupa digitação
                // na tela pequena com a mão suja de graxa.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Óleo", "Relação", "Pneu", "Revisão").forEach { s ->
                        Box(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(SuperficieAlta)
                                .clickable { nome = s }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(s, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
                        }
                    }
                }
                CampoFormulario("O que é", nome, { nome = it }, numerico = false)
                CampoFormulario(
                    "A cada quantos km", intervalo, { intervalo = it.filter(Char::isDigit) },
                    erro = intervalo.isNotBlank() && (km == null || km <= 0),
                    ajuda = "óleo de moto: 1.000 a 3.000 km, conforme o óleo"
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(3.dp)
                ) {
                    listOf(false to "Última troca em", true to "Próxima troca em").forEach { (proxima, rotulo) ->
                        val ativo = pelaProxima == proxima
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (ativo) Lima.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { pelaProxima = proxima }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                rotulo, style = MaterialTheme.typography.labelMedium,
                                color = if (ativo) Lima else TextoSecundario, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                CampoFormulario(
                    if (pelaProxima) "Km da próxima troca (etiqueta)" else "Km em que fez a última troca",
                    ultima, { ultima = it.filter(Char::isDigit).take(7) },
                    erro = ultima.isNotBlank() && (odo == null || odo < 0),
                    ajuda = if (odometroAtual > 0) "hoje a moto está em ${odometroAtual.kmPainel()} km" else null
                )
                // A conta pronta, antes de salvar: é aqui que o engano aparece.
                if (km != null && km > 0 && odo != null && odo >= 0) {
                    val vence = odo + km
                    val faltam = vence - odometroAtual
                    Text(
                        if (odometroAtual > 0) {
                            if (faltam > 0) "Próxima em ${vence.kmPainel()} km · faltam ${faltam.kmPainel()} km"
                            else "Próxima em ${vence.kmPainel()} km · já passou ${(-faltam).kmPainel()} km"
                        } else "Próxima em ${vence.kmPainel()} km",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(corDaManutencao(if (odometroAtual > 0) faltam else Long.MAX_VALUE).copy(alpha = 0.12f))
                            .padding(10.dp),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        color = corDaManutencao(if (odometroAtual > 0) faltam else Long.MAX_VALUE)
                    )
                    if (odometroAtual in 1 until odo) {
                        Text(
                            "A última troca está depois do km de hoje. Confira o número.",
                            style = MaterialTheme.typography.labelSmall, color = AmareloAlerta
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = {
                    onSalvar(
                        ItemManutencao(
                            id = inicial?.id ?: 0L,
                            nome = nome.trim(),
                            intervaloKm = km ?: 0L,
                            odometroUltimaKm = odo ?: 0L,
                            atualizadoEm = System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("Salvar", color = if (valido) Lima else TextoSecundario) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
