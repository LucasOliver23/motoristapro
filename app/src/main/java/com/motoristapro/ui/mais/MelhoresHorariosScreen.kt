package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.motoristapro.data.local.dao.FaixaHoraria
import com.motoristapro.service.AppNavegacao
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale("pt", "BR")
private val DIA_MES = DateTimeFormatter.ofPattern("dd MMM", PT_BR)

/** Quanto tempo para trás a tela olha. */
private enum class JanelaTempo(val rotulo: String, val dias: Long) {
    SEMANA("Semana", 7),
    MES("Mês", 30),
    TRIMESTRE("90 dias", 90)
}

/**
 * Os dias da semana como o SQLite numera no strftime('%w'): 0 = domingo.
 * -1 é o "Todos" — some do WHERE e deixa a consulta olhar a semana inteira.
 */
private val DIAS = listOf(
    -1 to "Todos", 1 to "Seg", 2 to "Ter", 3 to "Qua", 4 to "Qui", 5 to "Sex", 6 to "Sáb", 0 to "Dom"
)

/** Abaixo disto a faixa é curiosidade, não informação. */
private const val POUCOS_DADOS = 5

/**
 * "Melhores horários": em que faixa de 2 h o aplicativo costuma mandar corrida boa.
 *
 * Não grava nada novo — lê as ofertas que o leitor já salvou desde sempre, com
 * hora, valor e km. Por isso funciona desde o primeiro dia de uso, e fica melhor
 * sozinha conforme o motorista roda.
 *
 * Mostra R$/km E quantidade lado a lado de propósito: horário que paga bem mas
 * quase não toca corrida não enche tanque, e horário cheio de corrida ruim
 * também não.
 */
@Composable
fun MelhoresHorariosScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as MotoristaApp).repository }

    var janela by rememberSaveable { mutableStateOf(JanelaTempo.SEMANA) }
    var dia by rememberSaveable { mutableStateOf(-1) }

    val hoje = LocalDate.now()
    val primeiroDia = hoje.minusDays(janela.dias - 1)
    val zona = ZoneId.systemDefault()
    val inicio = primeiroDia.atStartOfDay(zona).toInstant().toEpochMilli()
    val fim = hoje.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()

    val fluxo = remember(inicio, fim, dia) { repo.faixasHorariasDoDia(inicio, fim, dia) }
    val faixas by fluxo.collectAsStateWithLifecycle(initialValue = emptyList())

    Seletor(
        opcoes = JanelaTempo.entries.map { it.rotulo },
        indiceAtivo = JanelaTempo.entries.indexOf(janela),
        onEscolher = { janela = JanelaTempo.entries[it] }
    )
    Text(
        "${primeiroDia.format(DIA_MES)} — ${hoje.format(DIA_MES)}",
        style = MaterialTheme.typography.bodySmall,
        color = TextoSecundario,
        modifier = Modifier.padding(start = 4.dp)
    )

    // Dias da semana numa fita que rola: cabe no celular estreito sem apertar.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DIAS.forEach { (numero, rotulo) ->
            val ativo = numero == dia
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (ativo) Lima else SuperficieAlta)
                    .clickable { dia = numero }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    rotulo,
                    color = if (ativo) Fundo else TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (faixas.isEmpty()) {
        CardSecao {
            Text("Ainda sem ofertas neste período", fontWeight = FontWeight.Bold)
            Text(
                "O app monta esta tela com as ofertas que o leitor vê na sua tela. " +
                    "Rode alguns dias com o leitor ligado e os horários aparecem sozinhos.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        return
    }

    val porReaisKm = faixas.sortedByDescending { it.reaisPorKmCentavos }
    val melhor = porReaisKm.first()
    val maisMovimento = faixas.maxByOrNull { it.ofertas }!!
    val totalOfertas = faixas.sumOf { it.ofertas }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Destaque(
            titulo = "Paga melhor",
            valor = melhor.rotulo,
            detalhe = "${melhor.reaisPorKmCentavos.emReais()}/km · ${melhor.ofertas} ofertas",
            cor = Lima,
            modifier = Modifier.weight(1f)
        )
        Destaque(
            titulo = "Mais movimento",
            valor = maisMovimento.rotulo,
            detalhe = "${maisMovimento.ofertas} ofertas · ${maisMovimento.reaisPorKmCentavos.emReais()}/km",
            cor = AmareloAlerta,
            modifier = Modifier.weight(1f)
        )
    }

    CardSecao(titulo = "Hora a hora") {
        Text(
            "Barra cheia = a faixa que mais paga por km. O número do lado é quantas " +
                "ofertas apareceram nela.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
        val teto = porReaisKm.first().reaisPorKmCentavos.coerceAtLeast(1)
        val mediana = porReaisKm[porReaisKm.size / 2].reaisPorKmCentavos
        porReaisKm.forEach { f ->
            LinhaFaixaHoraria(f, teto = teto, mediana = mediana)
        }
        Text(
            "$totalOfertas ofertas lidas neste recorte.",
            style = MaterialTheme.typography.labelSmall, color = TextoSecundario
        )
    }

    if (totalOfertas < POUCOS_DADOS * 3) {
        CardSecao {
            Text(
                "Ainda são poucas ofertas para confiar nestes horários. A conta fica " +
                    "firme depois de umas duas semanas rodando com o leitor ligado.",
                style = MaterialTheme.typography.bodySmall, color = AmareloAlerta
            )
        }
    }

    CardSecao {
        Text(
            "Estes dados são seus e ficam só neste celular: são as ofertas que o leitor " +
                "já tinha guardado. Nenhum endereço entra nesta conta.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }
}

/** Cartão curto com o número que o motorista leva na cabeça. */
@Composable
private fun Destaque(
    titulo: String,
    valor: String,
    detalhe: String,
    cor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp)
    ) {
        Text(titulo, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cor)
        Text(detalhe, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
    }
}

/** Uma faixa de 2 h: barra proporcional ao R$/km, valor e quantas ofertas. */
@Composable
private fun LinhaFaixaHoraria(f: FaixaHoraria, teto: Long, mediana: Long) {
    val fatia = (f.reaisPorKmCentavos.toFloat() / teto).coerceIn(0.04f, 1f)
    val cor = when {
        f.reaisPorKmCentavos >= teto * 0.85 -> Lima
        f.reaisPorKmCentavos >= mediana -> AmareloAlerta
        else -> VermelhoPrejuizo
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            f.rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario,
            modifier = Modifier.width(76.dp)
        )
        Box(
            Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(Contorno)
        ) {
            Box(Modifier.fillMaxWidth(fatia).height(16.dp).clip(RoundedCornerShape(8.dp)).background(cor))
        }
        Text(
            "${f.reaisPorKmCentavos.emReais()}/km",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = cor,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            "${f.ofertas}",
            style = MaterialTheme.typography.labelSmall,
            color = if (f.ofertas < POUCOS_DADOS) Contorno else TextoSecundario,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// ------------------------------------------------------------------ navegação

/**
 * Para onde o botão "Ver embarque" do cartão da oferta manda o motorista.
 *
 * O app que não estiver instalado aparece apagado e não é escolhível: melhor
 * mostrar que a opção existe do que esconder e deixar o motorista procurando.
 */
@Composable
fun AppNavegacaoScreen() {
    val context = LocalContext.current
    var escolha by remember { mutableStateOf(AppNavegacao.lida(context)) }

    CardSecao(titulo = "Abrir endereço em") {
        Text(
            "Vale para o botão \"Ver embarque\" do cartão da oferta — o que você usa " +
                "para olhar a esquina antes de aceitar.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
        AppNavegacao.entries.forEach { app ->
            val temNoCelular = AppNavegacao.instalado(context, app)
            val ativo = app == escolha
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (ativo) MaterialTheme.colorScheme.primaryContainer else SuperficieAlta
                    )
                    .clickable(enabled = temNoCelular) {
                        AppNavegacao.definir(context, app)
                        escolha = app
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        app.rotulo,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (temNoCelular) MaterialTheme.colorScheme.onSurface else Contorno
                    )
                    Text(
                        if (temNoCelular) app.descricao else "não está instalado neste celular",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextoSecundario
                    )
                }
                if (ativo) Text("✓", color = Lima, fontWeight = FontWeight.Bold)
            }
        }
    }
}
