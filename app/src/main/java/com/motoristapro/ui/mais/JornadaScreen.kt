package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.jornada.EstadoJornada
import com.motoristapro.service.AppDeCorrida
import com.motoristapro.service.StatusApp
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.formatarCronometro
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import com.motoristapro.ui.theme.VermelhoPrejuizo
import kotlinx.coroutines.delay

/** Uma cor por estado, para a barra e o pontinho combinarem com a legenda. */
private fun corDoEstado(e: EstadoJornada): Color = when (e) {
    EstadoJornada.OFFLINE -> TextoSecundario
    EstadoJornada.AGUARDANDO -> AmareloAlerta
    EstadoJornada.BUSCANDO -> Turquesa
    EstadoJornada.ESPERANDO -> VermelhoPrejuizo
    EstadoJornada.EM_VIAGEM -> Lima
}

/**
 * Jornada: onde o tempo do turno foi parar.
 *
 * O número que interessa não é "trabalhei 10 horas", é "fiquei 4 horas esperando
 * corrida". Por isso o tempo aparece dividido, com a porcentagem ao lado: é ela
 * que mostra o tamanho do desperdício.
 *
 * BUSCANDO, ESPERANDO e EM VIAGEM ainda não são detectados sozinhos — dependem
 * de reconhecer as telas de dentro da corrida de cada aplicativo. Aparecem
 * zerados, de propósito: esconder daria a impressão de que o turno inteiro foi
 * só offline e espera.
 */
@Composable
fun JornadaScreen() {
    val context = LocalContext.current
    val app = remember { context.applicationContext as MotoristaApp }
    val relogio = remember { app.relogio }
    val jornada by app.repository.jornadaAtiva().collectAsStateWithLifecycle(initialValue = null)

    // Relógio da tela: só para o número correr na frente do motorista. O tempo
    // de verdade é somado no banco a cada mudança de estado.
    var tique by remember { mutableIntStateOf(0) }
    LaunchedEffect(jornada?.id, jornada?.pausadaEm) {
        while (true) {
            delay(1_000)
            tique++
            // Fecha o trecho de tempos em tempos para a divisão por estado andar
            // junto com o cronômetro, em vez de só pular quando o motorista volta
            // para um app de corrida.
            relogio.atualizarAgora()
        }
    }
    var auto by remember { mutableStateOf(relogio.inicioAutomatico) }

    val emAndamento = jornada?.ativa == true
    val pausada = jornada?.pausada == true
    val estadoAgora = relogio.estadoAtual()
    // O trecho aberto: o tempo que o banco ainda não recebeu. Entra no cronômetro
    // e na divisão por estado, senão o cronômetro corria de segundo em segundo e
    // a divisão pulava de 5 em 5 s — e as duas somas não fechavam na tela.
    val correndo = emAndamento && !pausada
    // Ler 'tique' aqui é o que manda a tela se redesenhar a cada segundo.
    val abertos = if (correndo && tique >= 0) relogio.segundosDoTrechoAberto() else 0L
    val estadoAberto = relogio.estadoDoTrechoAberto()
    val decorridos = (jornada?.segundosContados ?: 0L) + abertos

    // ------------------------------------------------------------- cronômetro
    CardSecao {
        Text(
            decorridos.formatarCronometro(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TEMPO TOTAL", style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
            Box(Modifier.weight(1f))
            Etiqueta(
                texto = when {
                    !emAndamento -> "Parada"
                    pausada -> "Pausada"
                    else -> estadoAgora.rotulo
                },
                cor = when {
                    !emAndamento -> TextoSecundario
                    pausada -> AmareloAlerta
                    else -> corDoEstado(estadoAgora)
                }
            )
        }

        if (!emAndamento) {
            Button(
                onClick = { relogio.iniciarNaMao() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Fundo)
            ) { Text("Iniciar jornada", fontWeight = FontWeight.Bold) }
        } else {
            OutlinedButton(
                onClick = { if (pausada) relogio.retomar() else relogio.pausar() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (pausada) "Retomar jornada" else "Pausar jornada") }
            OutlinedButton(onClick = { relogio.encerrar() }, modifier = Modifier.fillMaxWidth()) {
                Text("Encerrar jornada", color = VermelhoPrejuizo)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Começar sozinha", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Conta a partir do momento em que algum app fica Online",
                    style = MaterialTheme.typography.labelSmall, color = TextoSecundario
                )
            }
            Switch(
                checked = auto,
                onCheckedChange = { relogio.inicioAutomatico = it; auto = it }
            )
        }
    }

    // ---------------------------------------------------------- apps monitorados
    CardSecao(titulo = "Apps monitorados") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppDeCorrida.entries.forEach { aplicativo ->
                QuadroApp(
                    app = aplicativo,
                    status = relogio.status(aplicativo),
                    naMao = relogio.corrigidoNaMao(aplicativo),
                    modifier = Modifier.weight(1f),
                    onClick = { relogio.corrigirApp(aplicativo); tique++ }
                )
            }
        }
        Text(
            "O leitor só enxerga o app que está na frente, então o que aparece aqui é o " +
                "último estado que ele viu. Toque em um app para corrigir na mão.",
            style = MaterialTheme.typography.labelSmall, color = TextoSecundario
        )
    }

    // ------------------------------------------------------------- por estado
    CardSecao(titulo = "Onde foi o tempo") {
        val j = jornada
        val tempos = EstadoJornada.entries.map { estado ->
            val gravado = j?.let { segundosDe(it, estado) } ?: 0L
            estado to gravado + if (estado == estadoAberto) abertos else 0L
        }
        val total = tempos.sumOf { it.second }.coerceAtLeast(1)
        tempos.forEach { (estado, segundos) ->
            LinhaEstado(estado, segundos, (segundos * 100 / total).toInt())
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("Tempo total", style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            Box(Modifier.weight(1f))
            Text(tempos.sumOf { it.second }.formatarCronometro(), fontWeight = FontWeight.Bold)
        }
        Text(
            "Buscando, aguardando e em viagem ainda não são reconhecidos sozinhos — " +
                "faltam as telas de dentro da corrida de cada aplicativo.",
            style = MaterialTheme.typography.labelSmall, color = TextoSecundario
        )
    }

    // ------------------------------------------------------------- por app
    CardSecao(titulo = "Tempo online por app") {
        val j = jornada
        val porApp = AppDeCorrida.entries.map { aplicativo ->
            val gravado = j?.let { segundosOnline(it, aplicativo) } ?: 0L
            // O trecho aberto conta para TODO app que está online agora: duas
            // corridas de apps diferentes rodam no mesmo minuto.
            val online = relogio.status(aplicativo) == StatusApp.ONLINE
            aplicativo to gravado + if (online) abertos else 0L
        }
        val maior = porApp.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        porApp.forEach { (aplicativo, segundos) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    aplicativo.rotulo,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.3f)
                )
                Box(
                    Modifier.weight(0.5f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Contorno)
                ) {
                    Box(
                        Modifier.fillMaxWidth((segundos.toFloat() / maior).coerceIn(0f, 1f))
                            .height(10.dp).clip(RoundedCornerShape(5.dp)).background(Lima)
                    )
                }
                Text(
                    segundos.formatarCronometro(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.25f).padding(start = 8.dp)
                )
            }
        }
    }
}

private fun segundosDe(j: Jornada, e: EstadoJornada): Long = when (e) {
    EstadoJornada.OFFLINE -> j.segOffline
    EstadoJornada.AGUARDANDO -> j.segAguardando
    EstadoJornada.BUSCANDO -> j.segBuscando
    EstadoJornada.ESPERANDO -> j.segEsperando
    EstadoJornada.EM_VIAGEM -> j.segEmViagem
}

private fun segundosOnline(j: Jornada, a: AppDeCorrida): Long = when (a) {
    AppDeCorrida.UBER -> j.segUber
    AppDeCorrida.NOVE9 -> j.seg99
    AppDeCorrida.IFOOD -> j.segIfood
    AppDeCorrida.INDRIVE -> j.segInDrive
}

@Composable
private fun LinhaEstado(estado: EstadoJornada, segundos: Long, percentual: Int) {
    val cor = corDoEstado(estado)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(cor))
            Text(
                estado.rotulo,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Text("$percentual%", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            Text(
                segundos.formatarCronometro(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(Contorno).padding(top = 2.dp)
        ) {
            Box(
                Modifier.fillMaxWidth((percentual / 100f).coerceIn(0f, 1f))
                    .height(6.dp).clip(RoundedCornerShape(3.dp)).background(cor)
            )
        }
    }
}

@Composable
private fun QuadroApp(
    app: AppDeCorrida,
    status: StatusApp,
    naMao: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val cor = when (status) {
        StatusApp.ONLINE -> Lima
        StatusApp.OFFLINE -> TextoSecundario
        StatusApp.DESCONHECIDO -> Contorno
    }
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(SuperficieAlta)
            .border(1.dp, if (status == StatusApp.ONLINE) Lima else Contorno, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(app.sigla, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(app.rotulo, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        Text(
            status.rotulo + if (naMao) " ✎" else "",
            style = MaterialTheme.typography.labelSmall,
            color = cor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Etiqueta(texto: String, cor: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(cor.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.labelMedium, color = cor, fontWeight = FontWeight.Bold)
    }
}
