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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.motoristapro.service.CampoCartao
import com.motoristapro.service.Classificacao
import com.motoristapro.service.ConfigCartao
import com.motoristapro.service.EstiloCartao
import com.motoristapro.service.Oferta
import com.motoristapro.service.PosicaoCartao
import com.motoristapro.service.TamanhoCartao
import com.motoristapro.service.TemaCartao
import com.motoristapro.service.AvisoVoz
import com.motoristapro.service.LimitesOferta
import com.motoristapro.service.PaletaCartao
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.ModoTema
import com.motoristapro.ui.theme.PreferenciaTema
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo

/** Oferta de mentira usada só para o motorista ver o cartão antes de ir para a rua. */
private val OFERTA_EXEMPLO = Oferta(
    valor = 18.40, km = 11.0, minutos = 26,
    enderecos = listOf("Av. Paulista, 1578"),
    nota = 4.82
)

/**
 * "Estilo do cartão": escolhe o que aparece na janela que sobe por cima da Uber/99,
 * com o cartão de verdade desenhado em cima da tela mudando a cada toque.
 *
 * Tudo é salvo na hora (não tem botão Salvar): o motorista mexe, olha o preview e sai.
 */
@Composable
fun EstiloCartaoScreen(custoKmCentavos: Long) {
    val context = LocalContext.current
    val estilo = remember { EstiloCartao(context) }
    var cfg by remember { mutableStateOf(estilo.ler()) }

    fun mudar(novo: ConfigCartao) {
        cfg = novo
        estilo.salvar(novo)
    }

    Text(
        "Este é o cartão que sobe por cima do app de corrida. Ele é igual para todo mundo: " +
            "os números certos já vêm escolhidos.",
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )

    PreviaCartao(cfg, custoKmCentavos)

    CardSecao(titulo = "O que o cartão mostra") {
        Text(
            "Em cima, ACEITAR, ATENÇÃO ou RECUSAR. Embaixo, três números:\n" +
                "• Corrida de passageiro: lucro, R$/km e R$/hora.\n" +
                "• Entrega: lucro, R$/km e quantas paradas.\n" +
                "E a linha \"Lucro R$ X/h · meta R$ Y/h\", que é o que decide a cor.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }

    CardSecao(titulo = "Ajustes do cartão") {
        Rotulo("Tamanho da letra")
        Seletor(
            opcoes = TamanhoCartao.entries.map { it.rotulo },
            indiceAtivo = TamanhoCartao.entries.indexOf(cfg.tamanho),
            onEscolher = { mudar(cfg.copy(tamanho = TamanhoCartao.entries[it])) }
        )

        Rotulo("Onde o cartão aparece")
        Seletor(
            opcoes = PosicaoCartao.entries.map { it.rotulo },
            indiceAtivo = PosicaoCartao.entries.indexOf(cfg.posicao),
            onEscolher = { mudar(cfg.copy(posicao = PosicaoCartao.entries[it])) }
        )
        Text(
            "Deixe longe do botão de aceitar do app para não tocar errado com pressa.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )

        Rotulo("Some sozinho em ${cfg.segundosNaTela}s")
        Slider(
            value = cfg.segundosNaTela.toFloat(),
            onValueChange = { mudar(cfg.copy(segundosNaTela = it.toInt().coerceIn(5, 60))) },
            valueRange = 5f..60f
        )

        LinhaChave(
            titulo = "Cores para daltônico",
            detalhe = "Azul, amarelo e roxo no lugar de verde, amarelo e vermelho",
            marcado = cfg.paleta == PaletaCartao.DALTONICO,
            onMudar = { mudar(cfg.copy(paleta = if (it) PaletaCartao.DALTONICO else PaletaCartao.CLASSICA)) }
        )
        LinhaChave(
            titulo = "Botões no cartão",
            detalhe = "Registrar, Embarque e Destino (desligado: cartão menor; os atalhos ficam na notificação)",
            marcado = cfg.botoes,
            onMudar = { mudar(cfg.copy(botoes = it)) }
        )
        OutlinedButton(
            onClick = { mudar(estilo.restaurarPadrao().copy(tema = TemaCartao.ESCURO)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Voltar ao padrão") }
    }
}

/** O cartão desenhado como ele vai aparecer na rua. */
@Composable
private fun PreviaCartao(cfg: ConfigCartao, custoKmCentavos: Long) {
    if (cfg.tema == TemaCartao.NOVO || cfg.tema == TemaCartao.ESCURO) {
        PreviaCartaoNovo(cfg, custoKmCentavos)
        return
    }
    val classe = Classificacao.BOA
    val corFaixa = when (classe) {
        Classificacao.BOA -> Lima
        Classificacao.MEDIA -> AmareloAlerta
        Classificacao.RUIM -> VermelhoPrejuizo
    }
    val fundoCartao = when (cfg.tema) {
        TemaCartao.NOVO -> Color.White
        TemaCartao.COLORIDO -> Color(0xFF0288D1)
        TemaCartao.ESCURO -> Color(0xFF14181C)
        TemaCartao.CLARO -> Color(0xFFF2F4F7)
    }
    val corTexto = if (cfg.tema == TemaCartao.CLARO || cfg.tema == TemaCartao.NOVO) Color(0xFF101720) else Color.White
    val escala = cfg.tamanho.escala
    val valores = cfg.valores(OFERTA_EXEMPLO, custoKmCentavos.coerceAtLeast(70))

    // Moldura de celular: deixa claro que isto é a tela da Uber e não a do MotoristaPro.
    Box(
        Modifier.fillMaxWidth().height(230.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SuperficieAlta)
            .border(1.dp, Contorno, RoundedCornerShape(20.dp))
            .padding(10.dp),
        contentAlignment = when (cfg.posicao) {
            PosicaoCartao.TOPO -> Alignment.TopCenter
            PosicaoCartao.CENTRO -> Alignment.Center
            PosicaoCartao.BAIXO -> Alignment.BottomCenter
        }
    ) {
        Text(
            "tela do app de corrida",
            style = MaterialTheme.typography.labelSmall, color = TextoSecundario,
            modifier = Modifier.align(Alignment.Center).alpha(0.4f)
        )
        Column(
            Modifier.fillMaxWidth()
                .alpha(cfg.opacidade / 100f)
                .clip(RoundedCornerShape(16.dp))
                .background(fundoCartao)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (cfg.avisoEmDestaque) {
                Text(
                    if (cfg.mostrarApp) "${classe.rotulo}  •  R$ 18,40" else classe.rotulo,
                    color = if (cfg.tema == TemaCartao.COLORIDO) Color.White else corFaixa,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15 * escala).sp
                )
            }
            Text(
                valores.take(3).joinToString("   "),
                color = corTexto,
                fontWeight = FontWeight.Bold,
                fontSize = (22 * escala).sp
            )
            if (valores.size > 3) {
                Text(
                    valores.drop(3).joinToString("  •  "),
                    color = corTexto.copy(alpha = 0.85f),
                    fontSize = (13 * escala).sp
                )
            }
            Text(
                "✓ Registrar corrida      📍 Ver embarque",
                color = corTexto.copy(alpha = 0.8f),
                fontSize = (12 * escala).sp
            )
        }
    }
}

/**
 * Prévia do cartão branco / escuro: o mesmo desenho do cartão de verdade
 * (etiqueta, 3 números com a barrinha da faixa, botões), com a oferta de exemplo.
 */
@Composable
private fun PreviaCartaoNovo(cfg: ConfigCartao, custoKmCentavos: Long) {
    val context = LocalContext.current
    val limites = remember { LimitesOferta(context) }
    val regua = (context.applicationContext as com.motoristapro.MotoristaApp).regua.parametros.value
    val custo = custoKmCentavos.coerceAtLeast(70)
    val o = OFERTA_EXEMPLO
    val classe = limites.classificar(o, 0, custo, regua)
    val cor = Color(cfg.paleta.cor(classe))
    val escuro = cfg.tema == TemaCartao.ESCURO
    val fundo = if (escuro) Color(0xFF0B0F14) else Color.White
    val texto = if (escuro) Color.White else Color(0xFF101720)
    val e = cfg.tamanho.escala
    val topo = String.format(java.util.Locale("pt", "BR"), "R$ %.2f  ·  %d min  ·  %.1f km", o.valor, o.minutos, o.km)

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFD7DFDA))
            .padding(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Column(Modifier.align(Alignment.Center).alpha(cfg.opacidade / 100f)) {
            run {
                Text(
                    when (classe) {
                        Classificacao.BOA -> "✓  ACEITAR"
                        Classificacao.MEDIA -> "!  ATENÇÃO"
                        Classificacao.RUIM -> "✕  RECUSAR"
                    },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = (12 * e).sp,
                    modifier = Modifier.padding(start = 8.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp))
                        .background(cor).padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(fundo)
                    .border(3.dp, cor, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(topo, color = texto, fontWeight = FontWeight.Bold, fontSize = (12 * e).sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    cfg.numeros(o, custo).forEach { n ->
                        val faixa = limites.faixaDoCampo(n.campo, o, custo, regua)
                        Column {
                            Text(n.rotulo, color = texto.copy(alpha = 0.6f), fontSize = (10 * e).sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(width = 3.dp, height = (20 * e).dp).clip(RoundedCornerShape(2.dp))
                                        .background(faixa?.let { Color(cfg.paleta.cor(it)) } ?: Color(0xFFB0B7C0))
                                )
                                Text(
                                    n.valor, color = texto, fontWeight = FontWeight.Bold,
                                    fontSize = (20 * e).sp, modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
                if (cfg.botoes) Text(
                    "✓ Registrar     📍 Embarque     📍 Destino",
                    color = texto.copy(alpha = 0.75f), fontSize = (12 * e).sp
                )
            }
        }
    }
}

/** Voz do cartão: quais ofertas falar (Boa / Média / Ruim) e a velocidade da fala. */
@Composable
fun VozOfertasCard() {
    val context = LocalContext.current
    val voz = remember { AvisoVoz(context.applicationContext) }
    var classes by remember { mutableStateOf(voz.classes) }
    var velocidade by remember { mutableStateOf(voz.velocidade) }

    CardSecao(titulo = "Voz") {
        Text(
            "Quais ofertas a voz fala. Área de risco é avisada sempre.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                Classificacao.BOA to "Boa", Classificacao.MEDIA to "Média", Classificacao.RUIM to "Ruim"
            ).forEach { (c, rotulo) ->
                val marcada = c.name in classes
                val cor = when (c) {
                    Classificacao.BOA -> Lima
                    Classificacao.MEDIA -> AmareloAlerta
                    Classificacao.RUIM -> VermelhoPrejuizo
                }
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (marcada) cor.copy(alpha = 0.18f) else SuperficieAlta)
                        .border(1.dp, if (marcada) cor else Contorno, RoundedCornerShape(10.dp))
                        .clickable {
                            val novo = if (marcada) classes - c.name else classes + c.name
                            // Pelo menos uma: para calar tudo, a chave é "Aviso por voz".
                            if (novo.isNotEmpty()) {
                                classes = novo
                                voz.classes = novo
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (if (marcada) "● " else "○ ") + rotulo,
                        color = if (marcada) cor else TextoSecundario, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Rotulo(String.format(java.util.Locale("pt", "BR"), "Velocidade: %.1fx", velocidade))
        Slider(
            value = velocidade,
            onValueChange = {
                velocidade = (Math.round(it * 10) / 10f).coerceIn(0.8f, 1.5f)
                voz.velocidade = velocidade
            },
            valueRange = 0.8f..1.5f
        )
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
}

/** Botões colados num trilho só, estilo "PP P M G". Usado também pelos horários. */
@Composable
internal fun Seletor(opcoes: List<String>, indiceAtivo: Int, onEscolher: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        opcoes.forEachIndexed { i, rotulo ->
            val ativo = i == indiceAtivo
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (ativo) Lima else SuperficieAlta)
                    .clickable { onEscolher(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    rotulo,
                    color = if (ativo) Fundo else TextoSecundario,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun LinhaChave(titulo: String, detalhe: String, marcado: Boolean, onMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detalhe, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        }
        Switch(checked = marcado, onCheckedChange = onMudar)
    }
}

// ------------------------------------------------------------------ aparência do app

/** "Aparência": o preto-e-branco do app inteiro. */
@Composable
fun AparenciaScreen() {
    val context = LocalContext.current
    val modo = PreferenciaTema.modo

    CardSecao(titulo = "Cor do app") {
        Text(
            "Vale para todas as telas do MotoristaPro. O cartão que aparece na rua tem " +
                "a cor dele, em Estilo do cartão.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
        ModoTema.entries.forEach { m ->
            val ativo = m == modo
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ativo) MaterialTheme.colorScheme.primaryContainer else SuperficieAlta)
                    .clickable { PreferenciaTema.definir(context, m) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.rotulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(m.descricao, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
                }
                if (ativo) Text("✓", color = Lima, fontWeight = FontWeight.Bold)
            }
        }
    }
}
