package com.motoristapro.ui.mais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.service.Diagnostico
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.LinhaSwitch
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.componentes.TextoAjuda
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sub-tela "Leitor de ofertas": estado do leitor, bolha, OCR, diagnóstico e permissões. */
@Composable
internal fun LeitorConteudo(
    leitorOk: Boolean,
    bolha: Boolean,
    ocr: Boolean,
    diagnostico: Diagnostico?,
    onTestar: () -> Unit,
    onAcessibilidade: () -> Unit,
    onBolha: (Boolean) -> Unit,
    onOcr: (Boolean) -> Unit,
    onDetalhesDoApp: () -> Unit
) {
    CardSecao(titulo = "Leitor de ofertas") {
        Text(
            if (leitorOk) "Conectado e lendo Uber, 99 e iFood" else "Desconectado",
            color = if (leitorOk) Lima else VermelhoPrejuizo,
            fontWeight = FontWeight.SemiBold
        )
        if (!leitorOk) {
            TextoAjuda(
                "O Android desligou o leitor (acontece ao fechar o app pela tela de recentes ou por economia " +
                    "de bateria). Toque em Acessibilidade, DESLIGUE e LIGUE de novo o \"MotoristaPro – Leitor de ofertas\". " +
                    "Para não repetir: libere \"Início automático\" e deixe a bateria \"Sem restrições\" em Informações do app."
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTestar, modifier = Modifier.weight(1f)) { Text("Testar janela") }
            OutlinedButton(onClick = onAcessibilidade, modifier = Modifier.weight(1f)) {
                Text("Acessibilidade")
            }
        }
        LinhaSwitch(
            "Bolha flutuante",
            "Lucro do dia e cronômetro do turno por cima da Uber/99. Toque na bolha para abrir o app.",
            bolha, onBolha
        )
        LinhaSwitch(
            "Ler oferta pela imagem (OCR)",
            "Para apps que escondem o texto do card (como a 99). Lê a tela no próprio celular, " +
                "sem internet. Gasta um pouco mais de bateria. Android 11 ou mais novo.",
            ocr, onOcr
        )
    }
    DiagnosticoCard(diagnostico, leitorOk, ocr)
    CardSecao(titulo = "Permissões") {
        OutlinedButton(onClick = onDetalhesDoApp, modifier = Modifier.fillMaxWidth()) {
            Text("Informações do app (localização, bateria)")
        }
        TextoAjuda("Dica: tire o MotoristaPro da otimização de bateria para o leitor e o GPS não serem desligados.")
    }
}

private val FORMATO_HORA_DIAG = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Mostra o que o leitor viu na última oferta (ou tela da Uber/99/iFood).
 * "Copiar" permite mandar os textos para ajustar o reconhecimento sem precisar de computador.
 */
@Composable
internal fun DiagnosticoCard(d: Diagnostico?, leitorOk: Boolean, ocrLigado: Boolean) {
    val clipboard = LocalClipboardManager.current
    CardSecao(titulo = "Diagnóstico do leitor") {
        if (!leitorOk) {
            Text("Leitor desconectado: ligue-o em Acessibilidade.", color = VermelhoPrejuizo, style = MaterialTheme.typography.bodySmall)
            return@CardSecao
        }
        if (d == null) {
            TextoAjuda("Nenhuma oferta lida ainda. Abra a Uber, 99 ou iFood e espere uma oferta aparecer; depois volte aqui.")
            return@CardSecao
        }
        val hora = Instant.ofEpochMilli(d.momento).atZone(ZoneId.systemDefault()).format(FORMATO_HORA_DIAG)
        LinhaValor("Última leitura", hora)
        LinhaValor("App", d.pacotes.joinToString { com.motoristapro.data.repository.nomePlataforma(it) })
        LinhaValor("Janelas lidas / textos", "${d.janelas} / ${d.textos.size}")
        LinhaValor("Leitura por", d.origem)
        Text(
            d.reconhecida?.let { "Reconhecida: $it" }
                ?: if (d.textos.isEmpty()) "O app não expôs nenhum texto. Ligue \"Ler oferta pela imagem (OCR)\" acima."
                else "Oferta NÃO reconhecida nesta leitura.",
            color = if (d.reconhecida != null) Lima else AmareloAlerta,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        d.motivo?.takeIf { d.reconhecida == null }?.let { motivo ->
            TextoAjuda("Por quê: $motivo")
        }
        if (d.textos.isNotEmpty()) {
            Text(
                d.textos.take(40).joinToString(" | "),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextoSecundario,
                maxLines = 12
            )
        }
        OutlinedButton(
            onClick = {
                val texto = buildString {
                    appendLine("MotoristaPro - diagnóstico $hora")
                    appendLine("Apps: ${d.pacotes.joinToString()}  Janelas: ${d.janelas}  Leitura: ${d.origem}")
                    appendLine("Reconhecida: ${d.reconhecida ?: "não"}")
                    appendLine(
                        "Leitura por imagem: " + (if (ocrLigado) "ligada" else "DESLIGADA") +
                            "  Android: ${android.os.Build.VERSION.SDK_INT}  Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    )
                    d.motivo?.let { appendLine("Por quê: $it") }
                    appendLine("Textos:")
                    d.textos.forEach { appendLine(it) }
                }
                clipboard.setText(AnnotatedString(texto))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copiar textos (para enviar ao suporte)") }
    }
}
