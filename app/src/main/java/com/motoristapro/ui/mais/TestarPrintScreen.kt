package com.motoristapro.ui.mais

import com.motoristapro.ui.theme.VerdeLucro
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.service.AppDoPrint
import com.motoristapro.service.Classificacao
import com.motoristapro.service.LeituraDePrint
import com.motoristapro.service.LimitesOferta
import com.motoristapro.service.ReguaLucroHora
import com.motoristapro.MotoristaApp
import com.motoristapro.service.OcrDoPrint
import com.motoristapro.service.OfertaAccessibilityService
import com.motoristapro.ui.NavegacaoRapida
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.ChipsSelecao
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.componentes.TextoAjuda
import com.motoristapro.ui.emReaisDouble
import com.motoristapro.ui.formatarKm
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.util.Locale

private val PT_BR = Locale("pt", "BR")

/**
 * Sub-tela "Testar com print": o motorista escolhe a imagem de uma oferta e vê
 * o que o leitor faria com ela — a etiqueta, os números e, quando não lê, o
 * porquê. Serve para testar Uber e iFood sem ter conta neles.
 *
 * A imagem também chega pelo "Compartilhar" de outro app (WhatsApp, Galeria),
 * por [NavegacaoRapida.printCompartilhado].
 */
@Composable
internal fun TestarPrintScreen(
    custoKmCentavos: Long,
    tarifaMinimaCentavos: Long
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val limites = remember { LimitesOferta(context) }
    val regua by remember { (context.applicationContext as MotoristaApp).regua.parametros }.collectAsState()

    var app by rememberSaveable { mutableStateOf(AppDoPrint.UBER) }
    var print by remember { mutableStateOf<Uri?>(null) }
    var linhas by remember { mutableStateOf<OcrDoPrint.Linhas?>(null) }
    var lendo by remember { mutableStateOf(false) }
    var falhou by remember { mutableStateOf(false) }
    var avisoCartao by remember { mutableStateOf<String?>(null) }

    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) print = uri
    }

    // Print compartilhado: vale uma vez só (quem lê zera), mesmo com a tela já aberta.
    val compartilhado by NavegacaoRapida.printCompartilhado.collectAsState()
    LaunchedEffect(compartilhado) {
        compartilhado?.let {
            print = it
            NavegacaoRapida.printCompartilhado.value = null
        }
    }

    // Lê de novo só quando troca a imagem. Trocar o app reaproveita as linhas.
    LaunchedEffect(print) {
        val uri = print ?: return@LaunchedEffect
        lendo = true
        falhou = false
        avisoCartao = null
        linhas = OcrDoPrint.ler(context, uri)
        falhou = linhas == null
        lendo = false
    }
    val resultado = linhas?.let { LeituraDePrint.interpretar(it.baixo, it.inteiro, app) }

    CardSecao(titulo = "Testar com print") {
        TextoAjuda(
            "Escolha o print de uma oferta (da galeria ou que chegou no WhatsApp). O app lê do mesmo " +
                "jeito que o leitor lê na rua e mostra o que sairia no cartão. Não precisa ter conta no app do print."
        )
        Text("De qual app é o print?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        ChipsSelecao(
            opcoes = AppDoPrint.entries,
            selecionado = app,
            rotulo = { it.rotulo },
            onSelecionar = { app = it; avisoCartao = null }
        )
        Button(
            onClick = { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !lendo
        ) { Text(if (print == null) "Escolher print" else "Escolher outro print") }
        TextoAjuda("Dica: no WhatsApp, abra a foto, toque em Compartilhar e escolha o MotoristaPro.")
    }

    if (lendo) {
        CardSecao {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("Lendo o print…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    if (falhou) {
        CardSecao(titulo = "Resultado") {
            Text("Não consegui abrir essa imagem.", color = VermelhoPrejuizo, fontWeight = FontWeight.SemiBold)
            TextoAjuda("Tente outro print, ou salve a foto na galeria antes de escolher.")
        }
        return
    }
    val r = resultado ?: return

    CardSecao(titulo = "Resultado") {
        val o = r.oferta
        if (o == null) {
            Text("Oferta NÃO reconhecida", color = AmareloAlerta, fontWeight = FontWeight.Bold)
            r.motivo?.let { TextoAjuda("Por quê: $it") }
            return@CardSecao
        }
        val classe = limites.classificar(o, tarifaMinimaCentavos, custoKmCentavos, regua)
        val avaliacao = regua?.let { ReguaLucroHora.avaliar(o, it) }
        Text(
            classe.rotulo,
            color = when (classe) {
                Classificacao.BOA -> VerdeLucro
                Classificacao.MEDIA -> AmareloAlerta
                Classificacao.RUIM -> VermelhoPrejuizo
            },
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
        )
        LinhaValor("Valor", o.valor.emReaisDouble(), negrito = true)
        LinhaValor("Distância", o.km.formatarKm())
        LinhaValor("Tempo", if (o.minutos > 0) "${o.minutos} min" else "não aparece no card")
        LinhaValor("R$/km", o.reaisPorKm.emReaisDouble())
        if (o.minutos > 0) LinhaValor("R$/hora", o.reaisPorHora.emReaisDouble())
        if (custoKmCentavos > 0) {
            val lucro = o.lucroEstimado(custoKmCentavos)
            LinhaValor("Lucro estimado", lucro.emReaisDouble(), cor = if (lucro > 0) VerdeLucro else VermelhoPrejuizo)
        } else {
            LinhaValor("Lucro estimado", "faça o assistente de custo", cor = TextoSecundario)
        }
        if (avaliacao != null) {
            LinhaValor(
                "Lucro por hora de corrida",
                avaliacao.lucroPorHoraReais.emReaisDouble(),
                cor = if (avaliacao.lucroPorHoraReais >= avaliacao.alvoPorHoraReais) VerdeLucro else AmareloAlerta,
                negrito = true
            )
            LinhaValor("Precisa (sua meta)", avaliacao.alvoPorHoraReais.emReaisDouble())
            if (avaliacao.tempoPeloKm) {
                TextoAjuda("O card não mostra o tempo: ${avaliacao.minutos} min calculados pelo km.")
            }
            if (avaliacao.estimado) {
                TextoAjuda(
                    "Estimado: o app ainda não mediu " +
                        (if (avaliacao.fracaoEstimada) "quanto do seu turno é com corrida (usa 60%)" else "") +
                        (if (avaliacao.fracaoEstimada && avaliacao.tempoPeloKm && avaliacao.velocidadeEstimada) " nem " else "") +
                        (if (avaliacao.tempoPeloKm && avaliacao.velocidadeEstimada) "a sua velocidade nas corridas (usa 15 km/h)" else "") +
                        ". Vira o seu número com o uso."
                )
            }
        } else if (custoKmCentavos > 0) {
            TextoAjuda("Sem meta de lucro e horas por dia (Menu ▸ Suas metas): a cor vem das faixas do semáforo.")
        }
        o.nota?.let { LinhaValor("Nota do passageiro", String.format(PT_BR, "%.2f", it)) }
        if (o.paradas > 0) LinhaValor("Paradas", o.paradas.toString())
        if (o.devolucao) LinhaValor("Possível devolução", "sim", cor = AmareloAlerta)
        if (o.enderecos.isNotEmpty()) LinhaValor("Endereços achados", o.enderecos.size.toString())
        if (r.total > 1) LinhaValor("Ofertas no print", "${r.total} (vale a de melhor R$/km)")
        r.parte?.let { LinhaValor("Leu pela", it.rotulo) }
        if (r.parte == LeituraDePrint.Parte.INTEIRO) {
            TextoAjuda(
                "Atenção: só achei a oferta lendo o print inteiro. Na rua o leitor pula o topo da tela " +
                    "(mapa e cartão). Se esse card aparece no alto da tela, mande os textos para ajustar."
            )
        }
        OutlinedButton(
            onClick = {
                val mostrou = OfertaAccessibilityService.instancia?.mostrarOfertaDoPrint(o, app.pacote) == true
                avisoCartao = if (mostrou) null
                else "Ligue o leitor em Acessibilidade para ver no cartão flutuante."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ver no cartão de verdade") }
        avisoCartao?.let { Text(it, color = AmareloAlerta, style = MaterialTheme.typography.bodySmall) }
    }

    CardSecao(titulo = "O que o leitor leu") {
        if (r.linhas.isEmpty()) {
            TextoAjuda("Nenhum texto na imagem.")
            return@CardSecao
        }
        Text(
            r.linhas.take(60).joinToString(" | "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextoSecundario,
            maxLines = 14
        )
        TextoAjuda(
            "Os endereços saem trocados por \"Rua Exemplo\". Antes de mandar, confira se não aparece " +
                "o nome do passageiro."
        )
        OutlinedButton(
            onClick = {
                val aparelho = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                clipboard.setText(AnnotatedString(LeituraDePrint.textoParaEnviar(r, app, aparelho)))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copiar textos (para mandar e virar teste)") }
    }
}
