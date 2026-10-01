package com.motoristapro.ui.componentes

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Uma permissão que o app precisa, com o texto que explica POR QUE precisa. */
data class Liberacao(
    val titulo: String,
    val porque: String,
    val concedida: Boolean,
    val abrir: () -> Unit
)

/**
 * Lembra o que o motorista marcou como "já liberei".
 *
 * Existe por um motivo concreto: em MIUI (Xiaomi), o "Sem restrições" da tela do
 * fabricante nem sempre é o mesmo que o Android reporta — o app liberou, mas a
 * API continua dizendo que não. Sem esta saída, o card ficaria pedindo para sempre.
 */
private class Dispensadas(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("preparacao", Context.MODE_PRIVATE)

    fun lista(): Set<String> = prefs.getStringSet(CHAVE, emptySet()).orEmpty()

    fun dispensar(titulo: String) {
        prefs.edit().putStringSet(CHAVE, lista() + titulo).apply()
    }

    private companion object { const val CHAVE = "dispensadas" }
}

/**
 * Card de preparação: o que ainda falta liberar para o app funcionar de verdade.
 *
 * Some sozinho quando está tudo liberado — não fica ocupando a tela para sempre.
 * A ordem é a da importância: sem acessibilidade o leitor não lê nada; sem
 * bateria livre o Android desliga o serviço no meio do turno.
 */
@Composable
fun CardPreparacao(liberacoes: List<Liberacao>, modifier: Modifier = Modifier) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val guardadas = remember { Dispensadas(contexto) }
    var dispensadas by remember { mutableStateOf(guardadas.lista()) }

    // "Concedida" vale tanto o que o Android confirma quanto o que o motorista marcou.
    val pendentes = liberacoes.filterNot { it.concedida || it.titulo in dispensadas }
    if (pendentes.isEmpty()) return

    var mostrarXiaomi by remember { mutableStateOf(false) }
    val total = liberacoes.size
    val faltam = pendentes.size
    val feitas = total - faltam

    CardSecao(modifier = modifier, destaque = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Falta pouco pra começar", fontWeight = FontWeight.Bold)
                Text(
                    if (faltam == 1) "1 liberação rápida — leva menos de 1 minuto."
                    else "$faltam liberações rápidas — leva menos de 1 minuto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario
                )
            }
            Text("$feitas/$total", fontWeight = FontWeight.Bold, color = Lima)
        }

        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(SuperficieAlta)
        ) {
            Box(
                Modifier.fillMaxWidth(feitas / total.toFloat()).height(5.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Lima)
            )
        }

        pendentes.forEach { l ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(AmareloAlerta)
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(l.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(l.porque, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                TextButton(onClick = l.abrir) { Text("Liberar", color = Lima) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        guardadas.dispensar(l.titulo)
                        dispensadas = guardadas.lista()
                    }
                ) {
                    Text(
                        "Já liberei, pode sumir",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
            }
        }

        if (ehFabricanteAgressivo()) {
            OutlinedButton(
                onClick = { mostrarXiaomi = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Seu ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} precisa de mais um passo") }
        }
    }

    if (mostrarXiaomi) {
        AlertDialog(
            onDismissRequest = { mostrarXiaomi = false },
            title = { Text("Proteção no seu ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Aparelhos Xiaomi, Redmi, POCO, Realme e Oppo fecham apps em segundo plano " +
                            "para economizar bateria — é isso que desliga o leitor de ofertas no meio " +
                            "do turno. Para evitar:",
                        color = TextoSecundario
                    )
                    PassoNumerado(1, "Toque em \"Abrir configurações\" aqui embaixo.")
                    PassoNumerado(2, "Encontre o MotoristaPro na lista de apps.")
                    PassoNumerado(3, "Ligue a chave \"Início automático\".")
                    Text(
                        "Depois, em Informações do app, deixe a bateria em \"Sem restrições\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
            },
            confirmButton = {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Button(
                    onClick = {
                        ctx.abrirInicioAutomatico()
                        mostrarXiaomi = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Fundo)
                ) { Text("Abrir configurações") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarXiaomi = false }) { Text("Já ativei", color = TextoSecundario) }
            }
        )
    }
}

@Composable
private fun PassoNumerado(n: Int, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(Lima),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", color = Fundo, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Text(texto, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Fabricantes conhecidos por matar serviços em segundo plano mesmo com tudo liberado. */
fun ehFabricanteAgressivo(): Boolean = Build.MANUFACTURER.lowercase() in setOf(
    "xiaomi", "redmi", "poco", "realme", "oppo", "vivo", "oneplus", "huawei", "honor", "meizu"
)

/** true quando o Android já concordou em não matar o app por bateria. */
fun Context.bateriaLiberada(): Boolean = runCatching {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    pm.isIgnoringBatteryOptimizations(packageName)
}.getOrDefault(false)

/**
 * Abre a tela de "Início automático" do fabricante. Cada um esconde num lugar;
 * se nenhuma abrir, cai nas informações do app, que sempre existe.
 */
fun Context.abrirInicioAutomatico() {
    val tentativas = listOf(
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        Intent().setClassName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        )
    )
    for (i in tentativas) {
        val ok = runCatching {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (ok) return
    }
    // Nenhuma tela do fabricante abriu: a de informações do app serve de porta.
    runCatching {
        startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Pede ao Android para parar de otimizar a bateria deste app. */
fun Context.pedirBateriaLivre() {
    runCatching {
        startActivity(
            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(android.net.Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
