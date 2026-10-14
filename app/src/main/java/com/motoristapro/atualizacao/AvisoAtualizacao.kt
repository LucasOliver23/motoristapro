package com.motoristapro.atualizacao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.ui.componentes.CorSobreDestaque
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import kotlinx.coroutines.launch

/**
 * Procura versão nova SOZINHO toda vez que o app abre (ou volta para a frente),
 * e mostra o aviso por cima de qualquer tela — inclusive do login.
 *
 * Antes só procurava em Menu ▸ Versão do app ▸ Procurar atualização; quase
 * ninguém entra ali, e a versão nova ficava parada no GitHub.
 *
 * O Android SEMPRE pede um toque em "Instalar": app instalado fora da Play
 * Store não pode se atualizar escondido. O que dá para fazer é chegar até essa
 * tela do instalador sem o motorista ter que procurar nada — é o que isto faz.
 */
@Composable
fun AvisoAtualizacao() {
    val app = LocalContext.current.applicationContext as MotoristaApp
    val gerente = app.atualizacao
    val estado by gerente.estado.collectAsStateWithLifecycle()
    val escopo = rememberCoroutineScope()
    var pedirPermissao by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        val agora = System.currentTimeMillis()
        if (agora - ultimaVerificacao > INTERVALO_MINIMO_MS) {
            ultimaVerificacao = agora
            escopo.launch { runCatching { gerente.verificar(avisarSeAtualizado = false) } }
        }
        onPauseOrDispose { }
    }

    val nova = estado.disponivel ?: return
    // "Depois" vale até o app ser fechado de vez; na próxima abertura avisa de novo.
    if (nova.versionCode == dispensadaNestaSessao && !nova.obrigatoria) return

    AlertDialog(
        onDismissRequest = {
            if (!nova.obrigatoria && !estado.baixando) dispensadaNestaSessao = nova.versionCode
        },
        title = { Text("Nova versão ${nova.versionName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Você está na ${gerente.versaoInstalada}. A atualização baixa sozinha e " +
                        "o Android pede só um toque em \"Instalar\".",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (nova.notas.isNotBlank()) {
                    Text(nova.notas, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                if (estado.baixando) {
                    LinearProgressIndicator(
                        progress = { estado.progresso / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Lima
                    )
                    Text("Baixando... ${estado.progresso}%", style = MaterialTheme.typography.bodySmall)
                }
                if (pedirPermissao) {
                    Text(
                        "Na tela que abriu, ligue \"Permitir desta fonte\", volte e toque em Atualizar de novo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Lima
                    )
                }
                estado.mensagem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextoSecundario) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!gerente.podeInstalar()) {
                        pedirPermissao = true
                        gerente.abrirPermissaoDeInstalacao()
                    } else {
                        pedirPermissao = false
                        escopo.launch { runCatching { gerente.baixarEInstalar(nova) } }
                    }
                },
                enabled = !estado.baixando,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
            ) { Text(if (estado.baixando) "Baixando..." else "Atualizar agora", fontWeight = FontWeight.Bold) }
        },
        dismissButton = if (nova.obrigatoria) null else {
            {
                TextButton(onClick = { dispensadaNestaSessao = nova.versionCode }, enabled = !estado.baixando) {
                    Text("Depois", color = TextoSecundario)
                }
            }
        }
    )
}

/** Não procura de novo a cada troca de tela: no máximo uma vez a cada 10 minutos. */
private const val INTERVALO_MINIMO_MS = 10L * 60 * 1000
private var ultimaVerificacao = 0L

/** Estado do Compose (e não var comum): tocar em "Depois" tem que fechar o aviso na hora. */
private var dispensadaNestaSessao by mutableIntStateOf(-1)
