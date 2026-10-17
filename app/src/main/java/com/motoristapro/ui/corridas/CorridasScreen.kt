package com.motoristapro.ui.corridas

import com.motoristapro.ui.componentes.Segmentado
import com.motoristapro.ui.componentes.CorSobreDestaque
import com.motoristapro.ui.componentes.chaveDaMarca
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.DivisorLista
import com.motoristapro.ui.componentes.Pilula
import com.motoristapro.ui.componentes.PilulaDia
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.componentes.SeloTexto
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.EstiloValor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.data.local.model.CorridaComPlataforma
import com.motoristapro.data.repository.ORIGEM_LEITOR
import com.motoristapro.data.repository.emReais
import com.motoristapro.service.AcoesOferta
import kotlinx.coroutines.launch
import com.motoristapro.ui.componentes.ChipsSelecao
import com.motoristapro.ui.componentes.ConfirmarExclusao
import com.motoristapro.ui.componentes.CorridaDialog
import com.motoristapro.ui.componentes.Metrica
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.componentes.Vazio
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.VermelhoPrejuizo
import com.motoristapro.ui.theme.TextoSecundario
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.LogoPlataforma
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha

private val PT = Locale("pt", "BR")
private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm", PT)

/** "qui, 1 out" — o cabeçalho de cada dia da lista. */
private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEE, d MMM", PT)

private fun diaDa(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** Cor da etiqueta por plataforma (usa a cor cadastrada; Uber preta ganha contorno claro). */
private fun corPlataforma(hex: String?): Color = try {
    if (hex.isNullOrBlank()) Lima else Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Lima
}

@Composable
fun CorridasRoute(vm: CorridasViewModel = viewModel(factory = CorridasViewModel.Factory)) {
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val modo by vm.modo.collectAsStateWithLifecycle()
    val ofertas by vm.ofertasState.collectAsStateWithLifecycle()
    val plataformas by vm.plataformas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    // Abrir o mapa é suspenso (o Geocoder vai à rede); o escopo da tela segura
    // isso sem travar a lista enquanto o endereço é convertido em coordenada.
    val escopo = rememberCoroutineScope()

    var editando by remember { mutableStateOf<Corrida?>(null) }
    var nova by remember { mutableStateOf(false) }
    var excluir by remember { mutableStateOf<CorridaComPlataforma?>(null) }
    var excluirOferta by remember { mutableStateOf<OfertaRecebida?>(null) }

    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }

    val coresPorId = remember(plataformas) { plataformas.associate { it.id to corPlataforma(it.corHex) } }

    TelaAba(
        titulo = "Corridas",
        subtitulo = if (modo == ModoLista.CORRIDAS) "${estado.resumo.qtdCorridas} registradas no período"
        else "${ofertas.resumo.total} ofertas lidas no período",
        snackbar = snackbar,
        // Ofertas lidas x corridas registradas: troca no canto, sem ocupar uma linha da tela.
        acoes = {
            Pilula(
                if (modo == ModoLista.OFERTAS) "Registradas" else "Ofertas",
                selecionada = false,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                vm.selecionarModo(if (modo == ModoLista.OFERTAS) ModoLista.CORRIDAS else ModoLista.OFERTAS)
            }
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nova = true },
                containerColor = Lima,
                contentColor = Color(0xFF0A0D0B),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Corrida", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { FitaDePilulas(estado.dia, estado.filtro, onDia = vm::escolherDia, onPeriodo = vm::selecionar) }
            if (modo == ModoLista.OFERTAS) {
                item {
                    FiltrosDaLista(
                        ofertas = ofertas,
                        onAceite = vm::filtrarAceite,
                        onPlataforma = vm::filtrarPlataforma
                    )
                }
                item { ResumoDoDia(estado, ofertas, onLimparSuspeitas = { vm.limparSuspeitas() }) }
                if (ofertas.ofertas.isEmpty()) {
                    item {
                        Vazio(
                            if (ofertas.todas.isEmpty())
                                "Nenhuma oferta lida neste período.\nAs ofertas aparecem aqui assim que o leitor mostra a janela."
                            else "Nenhuma oferta com esses filtros.\nToque em \"Todas\" para ver tudo."
                        )
                    }
                }
                // Com um dia só na tela o cabeçalho do dia é repetição; em
                // Semana/Mês ele volta, com o total de cada dia.
                val porDia = ofertas.ofertas.groupBy { diaDa(it.recebidaEm) }
                porDia.forEach { (dia, doDia) ->
                    if (estado.dia == null) {
                        item(key = "d$dia") {
                            CabecalhoDoDia(
                                dia = dia,
                                totalCentavos = doDia.filterNot { it.leituraSuspeita }.sumOf { it.valorCentavos },
                                quantas = doDia.size
                            )
                        }
                    }
                    items(doDia, key = { "o" + it.id }) { oferta ->
                        OfertaItem(
                            oferta,
                            custoKmCentavos = ofertas.custoKmCentavos,
                            onExcluir = { excluirOferta = oferta },
                            onVerNoMapa = { endereco ->
                                escopo.launch { AcoesOferta.abrirLocal(contexto, endereco) }
                            }
                        )
                    }
                }
                return@LazyColumn
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metrica("Faturamento", estado.resumo.faturamentoCentavos.emReais(), Modifier.weight(1f))
                    Metrica(
                        "Km", String.format(PT, "%.1f", estado.resumo.kmRodados), Modifier.weight(1f),
                        detalhe = if (estado.resumo.metrosRodados > 0) "${estado.resumo.ganhoPorKmCentavos.emReais()}/km" else null
                    )
                }
            }
            if (!estado.carregando && estado.corridas.isEmpty()) {
                item { Vazio("Nenhuma corrida neste período.\nUse o botão \"Registrar\" na janela da oferta ou o + abaixo.") }
            }
            items(estado.corridas, key = { it.corrida.id }) { item ->
                CorridaItem(
                    item = item,
                    cor = coresPorId[item.corrida.plataformaId] ?: Lima,
                    onClick = { editando = item.corrida },
                    onExcluir = { excluir = item }
                )
            }
        }
    }

    if (nova || editando != null) {
        CorridaDialog(
            plataformas = plataformas,
            inicial = editando,
            onDismiss = { nova = false; editando = null },
            onSalvar = { vm.salvar(it); nova = false; editando = null }
        )
    }
    excluirOferta?.let { alvo ->
        ConfirmarExclusao(
            texto = "Excluir a leitura de ${alvo.valorCentavos.emReais()} (${alvo.plataforma})?" +
                (if (alvo.registrada) "\n\nA corrida registrada por ela também sai do lucro do dia." else "") +
                if (alvo.leituraSuspeita) "\n\nEsta leitura está marcada como erro do leitor." else "",
            onDismiss = { excluirOferta = null },
            onConfirmar = { vm.excluirOferta(alvo.id); excluirOferta = null }
        )
    }
    excluir?.let { alvo ->
        ConfirmarExclusao(
            texto = "Excluir a corrida de ${alvo.corrida.receitaCentavos.emReais()} (${alvo.plataformaNome})?",
            onDismiss = { excluir = null },
            onConfirmar = { vm.excluir(alvo.corrida.id); excluir = null }
        )
    }
}

@Composable
private fun CorridaItem(item: CorridaComPlataforma, cor: Color, onClick: () -> Unit, onExcluir: () -> Unit) {
    val c = item.corrida
    val km = (c.distanciaMetros + c.deslocamentoMetros) / 1000.0
    val porKm = if (km > 0) c.receitaCentavos / km / 100.0 else 0.0
    val hora = Instant.ofEpochMilli(c.inicioEm).atZone(ZoneId.systemDefault()).format(FORMATO_HORA)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = cor.copy(alpha = 0.25f)) {
                        Text(
                            item.plataformaNome,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (cor == Color.Black) Color.White else cor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(hora, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                Text(
                    String.format(PT, "%.1f km • %d min • R$ %.2f/km", km, c.duracaoSegundos / 60, porKm),
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                if (c.destino.isNotBlank() || c.origem != ORIGEM_LEITOR) {
                    Text(
                        listOf(c.origem, c.destino).filter { it.isNotBlank() && it != "—" }.joinToString(" → "),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1
                    )
                }
            }
            Text(
                c.receitaCentavos.emReais(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Lima
            )
            IconButton(onClick = onExcluir) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario)
            }
        }
    }
}

/**
 * A fita dos últimos 7 dias: quanto o leitor viu passar em cada dia.
 *
 * Serve de mapa, não de gráfico de precisão — por isso sem eixo e sem número.
 * A barra mais alta é a do melhor dia, e é ela que o olho procura.
 */
@Composable
private fun FitaDeDias(todas: List<OfertaRecebida>) {
    val hoje = LocalDate.now()
    val dias = (6 downTo 0).map { hoje.minusDays(it.toLong()) }
    val porDia = todas.filterNot { it.leituraSuspeita }
        .groupBy { diaDa(it.recebidaEm) }
        .mapValues { (_, l) -> l.sumOf { it.valorCentavos } }
    val teto = (porDia.values.maxOrNull() ?: 0L).coerceAtLeast(1)

    CardSecao {
        Text(
            "${dias.first().format(FORMATO_FITA)} — ${dias.last().format(FORMATO_FITA)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            dias.forEach { d ->
                val valor = porDia[d] ?: 0L
                val fracao = (valor.toFloat() / teto).coerceIn(0.06f, 1f)
                val ehHoje = d == hoje
                Box(
                    Modifier.weight(1f).fillMaxHeight(fracao)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                valor == 0L -> Contorno
                                ehHoje || valor == teto -> Lima
                                else -> Lima.copy(alpha = 0.45f)
                            }
                        )
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dias.forEach { d ->
                Text(
                    "${d.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** As quatro marcas que o leitor conhece, na ordem em que aparecem no desenho. */
private val MARCAS_CONHECIDAS = listOf("uber", "99", "indrive", "ifood")

/**
 * A fila de selos das plataformas.
 *
 * Os quatro aplicativos aparecem SEMPRE, como no desenho — quem não teve oferta
 * no período fica apagado em vez de sumir. Fila que muda de tamanho conforme o
 * dia obriga a reaprender onde fica cada selo toda vez que se abre a aba.
 * Qualquer outro pacote que o leitor tenha visto entra no fim.
 */
@Composable
private fun FilaDePlataformas(
    plataformas: List<String>,
    escolhida: String?,
    quantidadeTotal: Int,
    onEscolher: (String?) -> Unit
) {
    val fila = MARCAS_CONHECIDAS + plataformas.filterNot { it in MARCAS_CONHECIDAS }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(if (escolhida == null) Lima else SuperficieAlta)
                .clickable { onEscolher(null) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "T",
                color = if (escolhida == null) Color(0xFF0A0D0B) else TextoSecundario,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fila.forEach { pacote ->
                val ativa = escolhida == pacote
                val teveOferta = pacote in plataformas
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(if (ativa) Lima else SuperficieAlta)
                        .alpha(if (teveOferta || ativa) 1f else 0.4f)
                        .clickable { onEscolher(pacote) },
                    contentAlignment = Alignment.Center
                ) {
                    LogoPlataforma(pacote, tamanho = if (ativa) 28.dp else 30.dp)
                }
            }
        }
        // O total do período, do lado direito, como no desenho.
        Box(
            Modifier.clip(RoundedCornerShape(99.dp)).background(SuperficieAlta)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                "$quantidadeTotal ›",
                style = MaterialTheme.typography.labelMedium,
                color = TextoSecundario,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Todas / Aceitas / Recusadas, com a contagem de cada uma. */
@Composable
private fun SegmentoAceite(
    atual: FiltroAceite,
    todas: Int,
    aceitas: Int,
    recusadas: Int,
    onEscolher: (FiltroAceite) -> Unit
) {
    val contagem = mapOf(
        FiltroAceite.TODAS to todas,
        FiltroAceite.ACEITAS to aceitas,
        FiltroAceite.RECUSADAS to recusadas
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SuperficieAlta).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FiltroAceite.entries.forEach { f ->
            val ativo = f == atual
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (ativo) MaterialTheme.colorScheme.background else Color.Transparent)
                    .clickable { onEscolher(f) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    f.rotulo,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal,
                    color = if (ativo) MaterialTheme.colorScheme.onSurface else TextoSecundario
                )
                Text(
                    "  ${contagem[f] ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario
                )
            }
        }
    }
}

/** Período da fita de dias: "28 set". */
private val FORMATO_FITA = DateTimeFormatter.ofPattern("d MMM", PT)

@Composable
private fun ResumoOfertasCard(e: OfertasUiState, onLimparSuspeitas: () -> Unit) {
    CardSecao {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${e.qtdNaLista} oferta(s) analisada(s)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                e.totalAnalisadoCentavos.emReais(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextoSecundario
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumeroDaLista("por corrida", e.mediaPorCorridaCentavos.emReais(), null, Modifier.weight(1f))
            NumeroDaLista("por hora", e.mediaPorHoraCentavos.emReais(), null, Modifier.weight(1f))
            NumeroDaLista("por km", e.mediaPorKmCentavos.emReais(), Lima, Modifier.weight(1f))
        }
        if (e.qtdSuspeitas > 0) {
            Text(
                "⚠ ${e.qtdSuspeitas} leitura(s) acima de R$ 50/km ficaram de fora das médias — " +
                    "o leitor pegou o número errado da tela do aplicativo.",
                style = MaterialTheme.typography.bodySmall, color = AmareloAlerta
            )
            OutlinedButton(onClick = onLimparSuspeitas, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = AmareloAlerta)
                Text("  Excluir as ${e.qtdSuspeitas} leituras com erro", color = AmareloAlerta)
            }
        }
    }
}

/**
 * Um número dentro de uma caixinha, repetido no resumo e em cada oferta.
 *
 * São sempre os mesmos três — por km, por hora e a nota — e sempre na mesma
 * ordem, para o olho achar sem ler o rótulo depois do terceiro dia de uso.
 */
@Composable
private fun NumeroDaLista(rotulo: String, valor: String, cor: Color?, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall.merge(EstiloValor),
            color = cor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
    }
}

/** Cabeçalho de um dia da lista: "QUI, 1 OUT" e o total do dia. */
@Composable
private fun CabecalhoDoDia(dia: LocalDate, totalCentavos: Long, quantas: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dia.format(FORMATO_DIA).uppercase(PT),
                style = MaterialTheme.typography.labelMedium,
                color = TextoSecundario,
                fontWeight = FontWeight.Bold
            )
            Text("$quantas oferta(s)", style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        }
        Text(totalCentavos.emReais(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OfertaItem(
    o: OfertaRecebida,
    custoKmCentavos: Long,
    onExcluir: () -> Unit,
    onVerNoMapa: (String) -> Unit
) {
    val hora = Instant.ofEpochMilli(o.recebidaEm).atZone(ZoneId.systemDefault()).format(FORMATO_HORA)
    val cor = when (o.classificacao) {
        "BOA" -> Lima
        "MEDIA" -> AmareloAlerta
        else -> VermelhoPrejuizo
    }
    // Fechado: uma linha só, como no desenho. Aberto (toque): os números, o
    // lucro, os endereços e a lixeira — nada do que existia se perdeu.
    var aberto by rememberSaveable(o.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Superficie)
            .clickable { aberto = !aberto }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // O pacote cru, não o nome bonito: um app novo que o leitor
            // ainda não conhece perde o apelido, mas não perde o logo.
            LogoPlataforma(o.plataforma, tamanho = 34.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    trajeto(o),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val partes = mutableListOf(hora, String.format(PT, "%.1f km", o.km))
                if (o.metros > 0) partes += "${o.reaisPorKmCentavos.emReais()}/km"
                Text(
                    partes.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(o.valorCentavos.emReais(), style = EstiloValor, fontSize = 17.sp)
                SeloTexto(
                    when (o.classificacao) { "BOA" -> "BOA"; "MEDIA" -> "MÉDIA"; else -> "RUIM" },
                    if (o.leituraSuspeita) AmareloAlerta else cor
                )
            }
        }

        if (!aberto) return@Column

            if (o.leituraSuspeita) {
                Text(
                    "⚠ Leitura suspeita: ${o.reaisPorKmCentavos.emReais()}/km. " +
                        "O leitor provavelmente pegou outro número da tela — esta oferta não entra nas médias.",
                    style = MaterialTheme.typography.bodySmall, color = AmareloAlerta
                )
            }

            // ---- os tres numeros que decidem a corrida
            // Sempre os tres, mesmo sem dado: lugar fixo e o que deixa comparar
            // duas ofertas de relance, sem reler os rotulos.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumeroDaLista(
                    "por km", o.reaisPorKmCentavos.emReais(),
                    if (o.leituraSuspeita) AmareloAlerta else cor, Modifier.weight(1f)
                )
                NumeroDaLista(
                    "por hora",
                    if (o.minutos > 0) o.reaisPorHoraCentavos.emReais() else "—",
                    null, Modifier.weight(1f)
                )
                NumeroDaLista(
                    "nota",
                    o.nota?.let { String.format(PT, "★ %.2f", it) } ?: "—",
                    o.nota?.let { AmareloAlerta }, Modifier.weight(1f)
                )
            }

            // ---- lucro: so faz sentido com o custo por km cadastrado
            if (custoKmCentavos > 0 && !o.leituraSuspeita) {
                val lucro = o.lucroCentavos(custoKmCentavos)
                val corLucro = if (lucro >= 0) Lima else VermelhoPrejuizo
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(corLucro.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Lucro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = corLucro,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${lucro.emReais()} (${o.lucroPercentual(custoKmCentavos)}%)",
                        fontWeight = FontWeight.Bold,
                        color = corLucro
                    )
                }
            }

            // ---- de onde pra onde, quando a tela mostrou
            // Endereço é para TOCAR: no histórico o motorista quer rever onde
            // foi a corrida que ele recusou — e decorar rua pelo nome não é
            // trabalho dele. Um toque abre no mapa (ou no Street View, conforme
            // o app de navegação escolhido no Menu).
            if (!o.origem.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    LinhaEndereco("●", o.origem!!, Lima) { onVerNoMapa(o.origem!!) }
                    o.destino?.takeIf { it.isNotBlank() }?.let { destino ->
                        LinhaEndereco("▾", destino, TextoSecundario) { onVerNoMapa(destino) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (o.registrada) "registrada ✓" else "não registrada",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (o.registrada) Lima else TextoSecundario,
                    modifier = Modifier.weight(1f)
                )
                // Leitura torta se apaga daqui: é esta lista que alimenta as
                // médias e os melhores horários.
                TextButton(onClick = onExcluir) {
                    Text("Excluir leitura", color = if (o.leituraSuspeita) AmareloAlerta else TextoSecundario)
                }
            }
    }
}

/** "Centro → Aeroporto" quando o leitor pegou os endereços; senão, o app e os km. */
private fun trajeto(o: OfertaRecebida): String {
    val origem = o.origem?.takeIf { it.isNotBlank() }?.let(::encurtar)
    val destino = o.destino?.takeIf { it.isNotBlank() }?.let(::encurtar)
    return when {
        origem != null && destino != null -> "$origem → $destino"
        origem != null -> origem
        else -> "Oferta de " + String.format(PT, "%.1f km", o.km)
    }
}

/** Só o começo do endereço (rua ou bairro), que é o que cabe numa linha. */
private fun encurtar(endereco: String): String = endereco.split(",", " - ").first().trim()
@Composable
private fun LinhaEndereco(marca: String, texto: String, cor: Color, onVerNoMapa: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable { onVerNoMapa() }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(marca, color = cor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // A setinha avisa que a linha abre alguma coisa; sem ela o toque é segredo.
        Text(
            "\u2197",
            style = MaterialTheme.typography.labelSmall,
            color = Lima,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// ------------------------------------------------------------------ visual novo (4.0)

private val FORMATO_DIA_CURTO = DateTimeFormatter.ofPattern("EEE", PT)

/**
 * A fita do topo: HOJE primeiro, à esquerda, e os 6 dias anteriores; rolando
 * para o lado, Semana / Mês / 30 dias. Um toque escolhe o período.
 * (Hoje no fim ficava fora da tela: era o dia mais usado e o mais escondido.)
 */
@Composable
private fun FitaDePilulas(
    dia: LocalDate?,
    filtro: FiltroPeriodo,
    onDia: (LocalDate) -> Unit,
    onPeriodo: (FiltroPeriodo) -> Unit
) {
    val hoje = LocalDate.now()
    val dias = (0..6).map { hoje.minusDays(it.toLong()) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dias.forEach { d ->
            PilulaDia(
                cima = if (d == hoje) "HOJE" else d.format(FORMATO_DIA_CURTO).take(3).uppercase(PT),
                baixo = "${d.dayOfMonth}",
                selecionada = d == dia,
                modifier = Modifier.width(52.dp)
            ) { onDia(d) }
        }
        listOf(FiltroPeriodo.SEMANA, FiltroPeriodo.MES, FiltroPeriodo.TRINTA_DIAS).forEach { f ->
            PilulaDia(
                cima = "VER",
                baixo = f.rotulo,
                selecionada = dia == null && filtro == f
            ) { onPeriodo(f) }
        }
    }
}

/**
 * Os apps pelo LOGO, sempre os quatro (mesmo sem oferta no dia: fila que muda
 * de tamanho obriga a reaprender onde fica cada um) e, embaixo, Todas /
 * Aceitas / Recusadas contando só o app escolhido.
 */
@Composable
private fun FiltrosDaLista(
    ofertas: OfertasUiState,
    onAceite: (FiltroAceite) -> Unit,
    onPlataforma: (String?) -> Unit
) {
    val marcas = (MARCAS_CONHECIDAS + ofertas.plataformasVistas).distinct()
    val escolhida = ofertas.plataforma
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LogoFiltro(
            nome = "Todos", marca = null, quantas = 0,
            selecionado = escolhida == null, apagado = false
        ) { if (escolhida != null) onPlataforma(escolhida) }
        marcas.forEach { marca ->
            LogoFiltro(
                nome = nomeDaMarca(marca), marca = marca,
                quantas = ofertas.todas.count { chaveDaMarca(it.plataforma) == marca },
                selecionado = escolhida == marca,
                apagado = escolhida != null && escolhida != marca
            ) { onPlataforma(marca) }
        }
    }
    val doApp = ofertas.todas.filter { escolhida == null || chaveDaMarca(it.plataforma) == escolhida }
    val aceitas = doApp.count { it.registrada }
    Segmentado(
        opcoes = listOf("Todas ${doApp.size}", "Aceitas $aceitas", "Recusadas ${doApp.size - aceitas}"),
        selecionado = ofertas.aceite.ordinal
    ) { onAceite(FiltroAceite.entries[it]) }
}

/** Um logo da fila de filtro: borda azul quando escolhido, apagado quando outro foi escolhido. */
@Composable
private fun LogoFiltro(
    nome: String,
    marca: String?,
    quantas: Int,
    selecionado: Boolean,
    apagado: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.width(62.dp).alpha(if (apagado) 0.35f else 1f).clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(15.dp))
                    .border(2.dp, if (selecionado) Lima else Color.Transparent, RoundedCornerShape(15.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (marca == null) {
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(SuperficieAlta),
                        contentAlignment = Alignment.Center
                    ) { Text("Todos", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
                } else {
                    LogoPlataforma(marca, tamanho = 44.dp)
                }
            }
            if (quantas > 0) {
                Text(
                    "$quantas",
                    modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(9.dp)).background(Lima)
                        .padding(horizontal = 5.dp),
                    color = CorSobreDestaque, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Text(
            nome, fontSize = 11.sp, maxLines = 1,
            color = if (selecionado) TextoPrincipal else TextoSecundario,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun nomeDaMarca(chave: String): String = when (chave) {
    "uber" -> "Uber"
    "99" -> "99"
    "ifood" -> "iFood"
    "indrive" -> "inDrive"
    else -> chave.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

/**
 * O resumo de cima: lucro, corridas e R$/km das corridas REGISTRADAS no período
 * e, embaixo, o que o leitor viu passar (ofertas e média por km).
 */
@Composable
private fun ResumoDoDia(estado: CorridasUiState, ofertas: OfertasUiState, onLimparSuspeitas: () -> Unit) {
    val r = estado.resumo
    Cartao(espaco = 10) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1.4f)) {
                Rotulo(if (estado.dia == LocalDate.now()) "Lucro do dia" else "Lucro no período")
                Text(
                    r.lucroLiquidoCentavos.emReais(), style = EstiloValor, fontSize = 22.sp,
                    color = if (r.lucroLiquidoCentavos >= 0) TextoPrincipal else VermelhoPrejuizo, maxLines = 1
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Rotulo("Corridas")
                Text("${r.qtdCorridas}", style = EstiloValor, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Rotulo("R$/km")
                Text(
                    if (r.metrosRodados > 0) r.ganhoPorKmCentavos.emReais().removePrefix("R$ ") else "—",
                    style = EstiloValor, fontSize = 22.sp
                )
            }
        }
        if (ofertas.todas.isNotEmpty()) {
            DivisorLista()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ofertas.qtdNaLista} ofertas lidas · média ${ofertas.mediaPorKmCentavos.emReais()}/km",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario,
                    modifier = Modifier.weight(1f)
                )
                if (ofertas.qtdSuspeitas > 0) {
                    TextButton(onClick = onLimparSuspeitas) {
                        Text("Limpar ${ofertas.qtdSuspeitas} com erro", color = AmareloAlerta)
                    }
                }
            }
        }
    }
}
