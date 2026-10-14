package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.service.Diagnostico
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo

/**
 * O checkup: tudo que o app precisa para funcionar, num lugar só.
 *
 * Nasceu do problema real: o Android derruba o leitor no meio do turno e o
 * motorista só descobre no fim do dia, com o histórico vazio. As peças já
 * existiam espalhadas — o card de preparação, o diagnóstico, o aviso de queda —
 * e separadas elas não respondiam a pergunta que importa, que é uma só:
 * "está tudo funcionando AGORA?".
 *
 * A ordem é a da gravidade. Sem o leitor não há leitura nenhuma; sem bateria
 * livre o leitor cai no meio do turno; sem notificação o aviso de queda não
 * chega. O resto é ajuste fino.
 */
@Composable
fun CheckupConteudo(
    leitorOk: Boolean,
    ocrLigado: Boolean,
    notificacoesOk: Boolean,
    bateriaOk: Boolean,
    diagnostico: Diagnostico?,
    onAcessibilidade: () -> Unit,
    onBateria: () -> Unit,
    onNotificacoes: () -> Unit,
    onInicializacao: () -> Unit,
    onOcr: () -> Unit,
    onTestar: () -> Unit
) {
    val itens = listOf(
        ItemCheckup(
            titulo = "Leitor de ofertas",
            estado = if (leitorOk) "lendo Uber, 99, iFood e inDrive" else "DESLIGADO — nada é lido",
            situacao = if (leitorOk) Situacao.OK else Situacao.GRAVE,
            porque = "É o que enxerga a oferta na tela. Sem ele o app não calcula nada.",
            acao = "Abrir Acessibilidade" to onAcessibilidade
        ),
        ItemCheckup(
            titulo = "Bateria sem restrições",
            estado = if (bateriaOk) "liberada" else "o sistema pode derrubar o leitor",
            situacao = if (bateriaOk) Situacao.OK else Situacao.ATENCAO,
            porque = "É a economia de bateria que desliga o leitor no meio do turno.",
            acao = "Liberar bateria" to onBateria
        ),
        ItemCheckup(
            titulo = "Inicialização automática",
            estado = "o fabricante decide — confira na lista",
            situacao = Situacao.CONFERIR,
            porque = "Xiaomi, Oppo, vivo e Huawei matam o app se ele não estiver nessa lista. " +
                "O Android não deixa o app saber se já está, então confira você.",
            acao = "Abrir a lista do fabricante" to onInicializacao
        ),
        ItemCheckup(
            titulo = "Notificações",
            estado = if (notificacoesOk) "permitidas" else "bloqueadas — o aviso de queda não chega",
            situacao = if (notificacoesOk) Situacao.OK else Situacao.ATENCAO,
            porque = "É por aqui que o app avisa na hora em que o leitor cai.",
            acao = "Abrir informações do app" to onNotificacoes
        ),
        ItemCheckup(
            titulo = "Leitura por imagem",
            estado = if (ocrLigado) "ligada" else "desligada — a 99 não é lida",
            situacao = if (ocrLigado) Situacao.OK else Situacao.ATENCAO,
            porque = "A 99 desenha o card por cima do mapa e não expõe o texto. " +
                "Sem a leitura por imagem, a oferta dela não aparece.",
            acao = (if (ocrLigado) "Desligar" else "Ligar") to onOcr
        )
    )

    val graves = itens.count { it.situacao == Situacao.GRAVE }
    val atencao = itens.count { it.situacao == Situacao.ATENCAO }

    CardSecao(centralizado = true) {
        val cor = when {
            graves > 0 -> VermelhoPrejuizo
            atencao > 0 -> AmareloAlerta
            else -> Lima
        }
        Text(
            when {
                graves > 0 -> "O app não está lendo"
                atencao > 0 -> "Funcionando, com ressalvas"
                else -> "Tudo funcionando"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = cor,
            textAlign = TextAlign.Center
        )
        Text(
            when {
                graves > 0 -> "Resolva o item em vermelho — sem ele nenhuma oferta entra no histórico."
                atencao > 0 -> "$atencao ponto(s) podem derrubar o leitor no meio do turno."
                else -> "Leitor ligado, bateria liberada e aviso de queda ativo."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onTestar, modifier = Modifier.fillMaxWidth()) {
            Text("Testar a janela da oferta")
        }
    }

    itens.forEach { LinhaCheckup(it) }

    DiagnosticoCard(diagnostico, leitorOk, ocrLigado)
}

private enum class Situacao { OK, ATENCAO, GRAVE, CONFERIR }

private class ItemCheckup(
    val titulo: String,
    val estado: String,
    val situacao: Situacao,
    val porque: String,
    val acao: Pair<String, () -> Unit>
)

@Composable
private fun LinhaCheckup(item: ItemCheckup) {
    val cor = when (item.situacao) {
        Situacao.OK -> Lima
        Situacao.ATENCAO -> AmareloAlerta
        Situacao.GRAVE -> VermelhoPrejuizo
        Situacao.CONFERIR -> TextoSecundario
    }
    CardSecao {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Bolinha(cor, item.situacao == Situacao.OK)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(item.estado, style = MaterialTheme.typography.bodySmall, color = cor)
            }
        }
        Text(item.porque, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        if (item.situacao != Situacao.OK) {
            OutlinedButton(onClick = item.acao.second, modifier = Modifier.fillMaxWidth()) {
                Text(item.acao.first)
            }
        } else {
            // Item resolvido ainda assim pode ser mexido — só não grita por isso.
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable { item.acao.second() }
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    item.acao.first,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextoSecundario,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** O sinal do item: cheio quando está certo, vazado quando falta. */
@Composable
private fun Bolinha(cor: Color, preenchida: Boolean) {
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .background(if (preenchida) cor.copy(alpha = 0.22f) else SuperficieAlta),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(11.dp).clip(CircleShape)
                .background(if (preenchida) cor else Contorno)
        )
    }
}

/** Os três atalhos do Início, no formato que o motorista reconhece de longe. */
@Composable
fun AtalhoRedondo(
    icone: String,
    rotulo: String,
    modifier: Modifier = Modifier,
    cor: Color? = null,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(SuperficieAlta),
            contentAlignment = Alignment.Center
        ) {
            Text(icone, fontSize = 20.sp)
        }
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = cor ?: TextoSecundario,
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
    }
}

/**
 * O mesmo atalho com ícone de traço fino (visual novo): o ícone ganha a cor do
 * assunto — amarelo no semáforo, azul no veículo — em vez da cor fixa do emoji.
 */
@Composable
fun AtalhoRedondo(
    icone: ImageVector,
    rotulo: String,
    corIcone: Color,
    modifier: Modifier = Modifier,
    cor: Color? = null,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(SuperficieAlta),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = cor ?: corIcone, modifier = Modifier.size(22.dp))
        }
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = cor ?: TextoSecundario,
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
    }
}
