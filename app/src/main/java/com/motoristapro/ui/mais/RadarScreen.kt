package com.motoristapro.ui.mais

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.radar.AvisoEvento
import com.motoristapro.radar.CalculoRadar
import com.motoristapro.radar.Evento
import com.motoristapro.radar.JanelaRadar
import com.motoristapro.radar.Movimento
import com.motoristapro.radar.TipoEvento
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import com.motoristapro.ui.theme.VermelhoPrejuizo
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HORA = DateTimeFormatter.ofPattern("HH'h'mm")

/**
 * Radar de eventos: jogos, shows e festas da cidade, com a hora de SAÍDA —
 * é quando o público vai embora que as chamadas chovem.
 *
 * A lista vem do MotoristaPro (publicada por quem administra o app) e o
 * motorista pode anotar os que viu. "Avisar" marca uma notificação 30 min
 * antes da saída.
 */
@Composable
fun RadarScreen(cidade: String?) {
    val context = LocalContext.current
    val app = context.applicationContext as MotoristaApp
    val radar = app.radar
    val escopo = rememberCoroutineScope()
    val eventos by radar.eventos.collectAsStateWithLifecycle()
    val admin by radar.admin.collectAsStateWithLifecycle()
    var janela by rememberSaveable { mutableStateOf(JanelaRadar.HOJE) }
    var avisos by remember { mutableStateOf(radar.avisosMarcados()) }
    var novo by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { radar.atualizar() } }

    val agora = System.currentTimeMillis()
    val lista = remember(eventos, janela, cidade) { CalculoRadar.filtrar(eventos, janela, cidade, agora) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (cidade.isNullOrBlank()) "Todas as cidades · defina a sua em Menu ▸ Meu perfil"
            else "Eventos em $cidade",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(4.dp)) {
        JanelaRadar.entries.forEach { j ->
            val ativo = j == janela
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (ativo) MaterialTheme.colorScheme.background else SuperficieAlta)
                    .clickable { janela = j }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(j.rotulo, color = if (ativo) Lima else TextoSecundario, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (lista.isEmpty()) {
        Cartao {
            Text("Nenhum evento ${janela.rotulo.lowercase()}", fontWeight = FontWeight.Bold)
            Text(
                "Quando sair jogo, show ou festa na sua cidade, ele aparece aqui com a hora de saída. " +
                    "Viu um evento que não está na lista? Toque em \"+ Adicionar evento\".",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
    }

    lista.forEach { e ->
        CartaoEvento(
            e = e,
            avisoLigado = e.id in avisos,
            podeApagar = e.doMotorista || admin,
            onAviso = { ligar ->
                if (ligar) {
                    if (AvisoEvento.agendar(context, e)) {
                        radar.marcarAviso(e.id, true)
                        Toast.makeText(context, "Aviso marcado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Já passou da hora do aviso", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    AvisoEvento.cancelar(context, e.id)
                    radar.marcarAviso(e.id, false)
                }
                avisos = radar.avisosMarcados()
            },
            onMapa = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.endereco ?: e.local)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            onApagar = {
                AvisoEvento.cancelar(context, e.id)
                if (e.doMotorista) radar.removerLocal(e.id)
                else escopo.launch {
                    runCatching { radar.apagarPublicado(e.id) }
                        .onFailure { Toast.makeText(context, "Não deu para apagar: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
        )
    }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.5.dp, Contorno, RoundedCornerShape(16.dp))
            .clickable { novo = true }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) { Text("+ Adicionar evento", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium) }

    Text(
        "Os eventos vêm de uma lista que o MotoristaPro atualiza. Os que você adiciona ficam só no seu celular.",
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Lima.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )

    if (novo) {
        NovoEventoDialog(
            cidade = cidade.orEmpty(),
            admin = admin,
            onDismiss = { novo = false },
            onSalvar = { e, paraTodos ->
                novo = false
                if (paraTodos) escopo.launch {
                    runCatching { radar.publicar(e) }
                        .onSuccess { Toast.makeText(context, "Publicado para todos", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Não deu para publicar: ${it.message}", Toast.LENGTH_LONG).show() }
                } else radar.adicionarLocal(e)
            }
        )
    }
}

@Composable
private fun CartaoEvento(
    e: Evento,
    avisoLigado: Boolean,
    podeApagar: Boolean,
    onAviso: (Boolean) -> Unit,
    onMapa: () -> Unit,
    onApagar: () -> Unit
) {
    val zona = ZoneId.systemDefault()
    val inicio = Instant.ofEpochMilli(e.inicioEm).atZone(zona).format(HORA)
    val saida = Instant.ofEpochMilli(e.saidaEm).atZone(zona).format(HORA)
    val horaAviso = CalculoRadar.horaDoAviso(e, System.currentTimeMillis())
    val corMov = when (e.movimento) {
        Movimento.ALTO -> VermelhoPrejuizo
        Movimento.MEDIO -> AmareloAlerta
        Movimento.BAIXO -> Turquesa
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Superficie).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(corMov.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Text(e.tipo.emoji, fontSize = 19.sp) }
            Column(Modifier.weight(1f)) {
                Text(e.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(e.local.takeIf { it.isNotBlank() }, "anotado por você".takeIf { e.doMotorista }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Início $inicio", TextoSecundario)
            Chip("Saída ~$saida", TextoSecundario)
            Chip("● ${e.movimento.rotulo}", corMov)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (horaAviso != null) {
                val h = Instant.ofEpochMilli(horaAviso).atZone(zona).format(HORA)
                Text(
                    if (avisoLigado) "🔔 Aviso às $h" else "🔕 Avisar às $h",
                    color = if (avisoLigado) Lima else TextoSecundario, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAviso(!avisoLigado) }.padding(4.dp)
                )
            }
            Box(Modifier.weight(1f))
            if (podeApagar) {
                Text(
                    "Apagar", color = TextoSecundario, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onApagar() }.padding(4.dp)
                )
            }
            Text(
                "Ver no mapa ›", color = Lima, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onMapa() }.padding(4.dp)
            )
        }
    }
}

@Composable
private fun Chip(texto: String, cor: Color) {
    Text(
        texto, color = cor, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(SuperficieAlta).padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

/** Anotar (ou publicar, para quem administra) um evento. Horas como "19:00". */
@Composable
private fun NovoEventoDialog(
    cidade: String,
    admin: Boolean,
    onDismiss: () -> Unit,
    onSalvar: (Evento, Boolean) -> Unit
) {
    var titulo by rememberSaveable { mutableStateOf("") }
    var local by rememberSaveable { mutableStateOf("") }
    var cidadeEv by rememberSaveable { mutableStateOf(cidade) }
    var tipo by rememberSaveable { mutableStateOf(TipoEvento.JOGO) }
    var movimento by rememberSaveable { mutableStateOf(Movimento.ALTO) }
    var dia by rememberSaveable { mutableStateOf(0) }
    var hInicio by rememberSaveable { mutableStateOf("") }
    var hSaida by rememberSaveable { mutableStateOf("") }
    var paraTodos by rememberSaveable { mutableStateOf(admin) }

    val inicio = lerHora(hInicio)
    val saidaHora = lerHora(hSaida)
    val valido = titulo.isNotBlank() && inicio != null && saidaHora != null && (!paraTodos || cidadeEv.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo evento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoEvento.entries.forEach { t ->
                        Escolha("${t.emoji} ${t.rotulo}", t == tipo) { tipo = t }
                    }
                }
                CampoFormulario("Nome (ex.: Goiás x Vila Nova)", titulo, { titulo = it }, numerico = false)
                CampoFormulario("Local ou endereço", local, { local = it }, numerico = false)
                if (admin) CampoFormulario("Cidade", cidadeEv, { cidadeEv = it }, numerico = false)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hoje", "Amanhã", "Depois").forEachIndexed { i, r -> Escolha(r, dia == i) { dia = i } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoFormulario("Início (19:00)", hInicio, { hInicio = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, Modifier.weight(1f), numerico = false)
                    CampoFormulario("Saída (21:00)", hSaida, { hSaida = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, Modifier.weight(1f), numerico = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Movimento.entries.forEach { m -> Escolha(m.rotulo.removePrefix("Movimento "), m == movimento) { movimento = m } }
                }
                if (admin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Publicar para todos os motoristas", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = paraTodos, onCheckedChange = { paraTodos = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Lima, checkedThumbColor = Color(0xFF0D1117))
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                val zona = ZoneId.systemDefault()
                val data = LocalDate.now().plusDays(dia.toLong())
                val ini = data.atTime(inicio!!).atZone(zona).toInstant().toEpochMilli()
                // Saída antes do início = passa da meia-noite (show das 22h que sai à 01h).
                val sai = (if (saidaHora!! < inicio) data.plusDays(1) else data).atTime(saidaHora)
                    .atZone(zona).toInstant().toEpochMilli()
                onSalvar(
                    Evento(
                        id = "", titulo = titulo.trim(), local = local.trim(), endereco = local.trim().ifBlank { null },
                        cidade = cidadeEv.trim(), tipo = tipo, inicioEm = ini, saidaEm = sai, movimento = movimento
                    ),
                    admin && paraTodos
                )
            }) { Text("Salvar", color = if (valido) Lima else TextoSecundario) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun Escolha(rotulo: String, ativo: Boolean, onClick: () -> Unit) {
    Text(
        rotulo,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (ativo) Lima else SuperficieAlta)
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (ativo) Color(0xFF0D1117) else TextoSecundario,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold
    )
}

/** "19:00", "19h", "1900", "19" → 19:00. */
private fun lerHora(t: String): LocalTime? {
    val d = t.filter(Char::isDigit)
    val (h, m) = when (d.length) {
        1, 2 -> d.toInt() to 0
        3 -> d.take(1).toInt() to d.drop(1).toInt()
        4 -> d.take(2).toInt() to d.drop(2).toInt()
        else -> return null
    }
    return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
}
