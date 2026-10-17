package com.motoristapro.ui.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.service.AvisoVoz
import com.motoristapro.service.OfertaAccessibilityService
import com.motoristapro.service.OfertaInDrive
import com.motoristapro.ui.abrirConfigAcessibilidade
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.DivisorLista
import com.motoristapro.ui.componentes.LogoPlataforma
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo

/** Os apps da fileira de logos, na ordem da tela. */
private val APPS = listOf(
    "com.ubercab.driver" to "Uber",
    "com.app99.driver" to "99",
    OfertaInDrive.PACOTE to "inDrive",
    OfertaAccessibilityService.PACOTE_IFOOD to "iFood"
)

/**
 * O Copiloto no topo do Início (4.8): as chaves do leitor na primeira tela,
 * como nos outros apps de motorista — ligar/desligar a leitura, escolher os
 * apps, bolha, voz, notificação e o atalho para a aparência do cartão.
 *
 * Tudo salva na hora e vale já na próxima oferta.
 */
@Composable
fun CopilotoCard(conectado: Boolean, onAparencia: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MotoristaApp
    val prefs = app.preferencias
    val pausado by prefs.leitorPausado.collectAsStateWithLifecycle()
    val desligados by prefs.appsDesligados.collectAsStateWithLifecycle()
    val bolha by prefs.bolhaFlow.collectAsStateWithLifecycle()
    val notificacao by prefs.notificacaoOferta.collectAsStateWithLifecycle()
    val voz = remember { AvisoVoz(app) }
    var vozLigada by remember { mutableStateOf(voz.ativo) }

    val lendo = conectado && !pausado
    Cartao(espaco = 6) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Copiloto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !conectado -> "● leitor desligado no Android — toque para ligar"
                        pausado -> "● pausado — não está lendo ofertas"
                        else -> "● lendo ofertas agora"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lendo) Lima else VermelhoPrejuizo,
                    modifier = if (!conectado) Modifier.clickable { context.abrirConfigAcessibilidade() } else Modifier
                )
            }
            Chave(lendo) {
                if (!conectado) context.abrirConfigAcessibilidade()
                else prefs.definirLeitorPausado(!pausado)
            }
        }

        // Toque no logo liga/desliga a leitura daquele app.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            APPS.forEach { (pacote, nome) ->
                val ligado = pacote !in desligados
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp))
                        .border(2.dp, if (ligado) Lima else Color.Transparent, RoundedCornerShape(11.dp))
                        .clickable { prefs.alternarApp(pacote) }
                        .padding(2.dp)
                        .alpha(if (ligado) 1f else 0.3f)
                ) { LogoPlataforma(nome, tamanho = 34.dp) }
            }
            Text(
                "toque para ligar/desligar",
                style = MaterialTheme.typography.labelSmall, color = TextoSecundario,
                modifier = Modifier.weight(1f)
            )
        }

        DivisorLista()
        LinhaCopiloto("⚪", "Bolha flutuante", "lucro do dia sempre à vista", bolha) {
            prefs.definirBolha(!bolha)
            OfertaAccessibilityService.instancia?.aplicarPreferencias()
        }
        LinhaCopiloto(
            "🔊", "Aviso por voz",
            if (voz.classes.size == 3) "fala todas as ofertas" else "só ofertas " +
                voz.classes.joinToString(", ") { when (it) { "BOA" -> "Boas"; "MEDIA" -> "Médias"; else -> "Ruins" } },
            vozLigada
        ) {
            voz.ativo = !vozLigada
            vozLigada = voz.ativo
            OfertaAccessibilityService.instancia?.aplicarPreferencias()
        }
        LinhaCopiloto("🔔", "Notificação da corrida", "endereços + atalhos do mapa", notificacao) {
            prefs.definirNotificacaoOferta(!notificacao)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onAparencia() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icone("🎨")
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Aparência do cartão", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("cores, números, tamanho, voz", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
            Text("›", color = Lima, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun LinhaCopiloto(icone: String, titulo: String, detalhe: String, ligado: Boolean, onMudar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onMudar() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icone(icone)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        }
        Chave(ligado) { onMudar() }
    }
}

@Composable
private fun Icone(t: String) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(SuperficieAlta),
        contentAlignment = Alignment.Center
    ) { Text(t, fontSize = 14.sp) }
}

@Composable
private fun Chave(ligado: Boolean, onMudar: () -> Unit) {
    Switch(
        checked = ligado,
        onCheckedChange = { onMudar() },
        colors = SwitchDefaults.colors(
            checkedTrackColor = Lima, checkedThumbColor = Fundo,
            uncheckedTrackColor = SuperficieAlta, uncheckedBorderColor = Contorno
        ),
        modifier = Modifier.height(30.dp)
    )
}
