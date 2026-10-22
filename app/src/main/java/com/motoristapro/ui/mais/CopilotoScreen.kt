package com.motoristapro.ui.mais

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.diagnostico.RegistroErros
import com.motoristapro.service.Diagnostico
import com.motoristapro.ui.componentes.BarraSegmentos
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.Tecla
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.SuperficieMaisAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VerdeLucro
import com.motoristapro.ui.theme.VermelhoPrejuizo

/**
 * O Copiloto (5.1, era o Checkup): tudo que o app precisa para funcionar, num
 * lugar só, com o porquê de cada autorização e um "Liberar" no que falta.
 *
 * Nasceu do problema real: o Android derruba o leitor no meio do turno e o
 * motorista só descobre no fim do dia, com o histórico vazio. A pergunta que
 * a tela responde é uma só: "está tudo funcionando AGORA?".
 *
 * A ordem é a da gravidade. Sem o leitor não há leitura nenhuma; sem aparecer
 * por cima o cartão não aparece; sem bateria livre o leitor cai no meio do
 * turno. O resto é ajuste fino.
 */
@Composable
fun CopilotoConteudo(
    estado: EstadoCopiloto,
    diagnostico: Diagnostico?,
    onAcessibilidade: () -> Unit,
    onSobreposicao: () -> Unit,
    onBateria: () -> Unit,
    onNotificacoes: () -> Unit,
    onInicializacao: () -> Unit,
    onOcr: () -> Unit,
    onLocalizacao: () -> Unit,
    onTestar: () -> Unit
) {
    val itens = buildList {
        add(
            ItemCopiloto(
                Icones.Olho, "Leitor de ofertas", "Acessibilidade · lê a oferta na tela",
                if (estado.leitor) Situacao.OK else Situacao.GRAVE, onAcessibilidade
            )
        )
        add(
            ItemCopiloto(
                Icones.Bolha, "Aparecer sobre outros apps", "mostra o cartão por cima da Uber e da 99",
                if (estado.sobreposicao) Situacao.OK else Situacao.GRAVE, onSobreposicao
            )
        )
        add(
            ItemCopiloto(
                Icones.Raio, "Bateria sem restrições", "o Android não desliga o leitor no meio do turno",
                if (estado.bateria) Situacao.OK else Situacao.ATENCAO, onBateria
            )
        )
        add(
            ItemCopiloto(
                Icones.Engrenagem, "Início automático", "Xiaomi, Oppo, vivo: confira se o app está na lista",
                Situacao.CONFERIR, onInicializacao
            )
        )
        add(
            ItemCopiloto(
                Icones.Sino, "Notificações", "resumo do dia, manutenção e aviso de leitor caído",
                if (estado.notificacoes) Situacao.OK else Situacao.ATENCAO, onNotificacoes
            )
        )
        estado.leituraImagem?.let { ok ->
            add(
                ItemCopiloto(
                    Icones.Documento, "Leitura por imagem", "para a 99, que esconde o texto do card",
                    if (ok) Situacao.OK else Situacao.ATENCAO, onOcr
                )
            )
        }
        add(
            ItemCopiloto(
                Icones.Alfinete, "Localização", "mede os km do turno pelo GPS",
                if (estado.localizacao) Situacao.OK else Situacao.ATENCAO, onLocalizacao
            )
        )
    }

    val graves = itens.count { it.situacao == Situacao.GRAVE }
    val cor = when {
        graves > 0 -> VermelhoPrejuizo
        !estado.tudoPronto -> AmareloAlerta
        else -> VerdeLucro
    }
    val falta = itens.firstOrNull { it.situacao == Situacao.GRAVE } ?: itens.firstOrNull { it.situacao == Situacao.ATENCAO }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Superficie).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("PRONTOS", color = TextoSecundario, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${estado.prontos}", style = EstiloValor, fontSize = 30.sp, color = cor)
            Text(" de ${estado.total}", style = EstiloValor, fontSize = 30.sp, color = TextoPrincipal)
        }
        BarraSegmentos(estado.prontos.toFloat() / estado.total.coerceAtLeast(1), cor, segmentos = estado.total)
        Text(
            when {
                falta == null -> "Tudo liberado: o app lê as ofertas e aguenta o turno inteiro."
                graves > 0 -> "Falta ${falta.titulo.lowercase()}: sem isso nenhuma oferta é lida."
                else -> "Falta ${estado.total - estado.prontos}: ${falta.titulo.lowercase()}."
            },
            color = TextoSecundario, fontSize = 12.sp
        )
    }

    itens.forEach { LinhaCopiloto(it) }

    Tecla(
        "Testar o cartão agora", fundo = SuperficieMaisAlta, corTexto = TextoPrincipal, borda = Color.Black,
        modifier = Modifier.fillMaxWidth(), onClick = onTestar
    )

    DiagnosticoCard(diagnostico, estado.leitor, estado.leituraImagem == true)

    UltimoErroCard()
}

/**
 * O último erro anotado pelo app (ver RegistroErros). Só aparece se houver:
 * é o que o motorista copia e manda quando o leitor "desliga sozinho".
 */
@Composable
private fun UltimoErroCard() {
    val context = LocalContext.current
    var texto by remember { mutableStateOf(RegistroErros.ler(context)) }
    val erro = texto ?: return
    CardSecao(titulo = "ÚLTIMO ERRO DO APP") {
        Text(
            "Se o leitor desligou sozinho, o motivo costuma estar aqui. Toque em Copiar e " +
                "mande para o suporte.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
        Text(
            erro.lines().take(8).joinToString("\n"),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SuperficieAlta).padding(10.dp),
            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = AmareloAlerta
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("Erro MotoristaPro", erro))
                    Toast.makeText(context, "Erro copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copiar") }
            OutlinedButton(
                onClick = { RegistroErros.apagar(context); texto = null },
                modifier = Modifier.weight(1f)
            ) { Text("Apagar") }
        }
    }
}

/** OK = liberado; ATENCAO e GRAVE pedem "Liberar"; CONFERIR o app não consegue ler. */
private enum class Situacao { OK, ATENCAO, GRAVE, CONFERIR }

private class ItemCopiloto(
    val icone: ImageVector,
    val titulo: String,
    val porque: String,
    val situacao: Situacao,
    val abrir: () -> Unit
)

/** Uma autorização: ícone na cor do estado, nome, o porquê e, se falta, a tecla "Liberar". */
@Composable
private fun LinhaCopiloto(item: ItemCopiloto) {
    val cor = when (item.situacao) {
        Situacao.OK -> VerdeLucro
        Situacao.ATENCAO -> AmareloAlerta
        Situacao.GRAVE -> VermelhoPrejuizo
        Situacao.CONFERIR -> TextoSecundario
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Superficie)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, cor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(item.icone, contentDescription = null, tint = cor, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.titulo, color = TextoPrincipal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(item.porque, color = TextoSecundario, fontSize = 11.5.sp)
        }
        when (item.situacao) {
            Situacao.OK -> Icon(Icones.Certo, contentDescription = "Liberado", tint = VerdeLucro, modifier = Modifier.size(22.dp))
            Situacao.CONFERIR -> Tecla(
                "Abrir", fundo = SuperficieMaisAlta, corTexto = TextoPrincipal, borda = Color.Black,
                altura = 44.dp, tamanhoTexto = 13, onClick = item.abrir
            )
            else -> Tecla(
                "Liberar", fundo = Color.White, corTexto = Color.Black, borda = TextoSecundario,
                altura = 44.dp, tamanhoTexto = 13, onClick = item.abrir
            )
        }
    }
}
