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
    paradas = 2, nota = 4.82
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
        "Este é o cartão que sobe por cima do app de corrida. Mexa abaixo e veja mudando.",
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )

    PreviaCartao(cfg, custoKmCentavos)

    CardSecao(titulo = "O que aparece no cartão") {
        Text(
            "Marque os números que importam para você. Use as flechas para pôr em cima " +
                "o que você olha primeiro.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
        // Primeiro os escolhidos, na ordem; depois o resto, para adicionar.
        cfg.campos.forEachIndexed { i, campo ->
            LinhaCampo(
                campo = campo,
                marcado = true,
                podeSubir = i > 0,
                podeDescer = i < cfg.campos.lastIndex,
                onMarcar = { mudar(cfg.alternando(campo)) },
                onSubir = { mudar(cfg.movendo(campo, -1)) },
                onDescer = { mudar(cfg.movendo(campo, +1)) }
            )
        }
        val fora = CampoCartao.entries.filter { it !in cfg.campos }
        if (fora.isNotEmpty()) {
            HorizontalDivider(color = Contorno)
            Text(
                "DESLIGADOS", style = MaterialTheme.typography.labelSmall,
                color = TextoSecundario, fontWeight = FontWeight.Bold
            )
            fora.forEach { campo ->
                LinhaCampo(
                    campo = campo,
                    marcado = false,
                    podeSubir = false,
                    podeDescer = false,
                    onMarcar = { mudar(cfg.alternando(campo)) },
                    onSubir = { }, onDescer = { }
                )
            }
        }
    }

    CardSecao(titulo = "Cores e tamanho") {
        Rotulo("Cor do cartão")
        Seletor(
            opcoes = TemaCartao.entries.map { it.rotulo },
            indiceAtivo = TemaCartao.entries.indexOf(cfg.tema),
            onEscolher = { mudar(cfg.copy(tema = TemaCartao.entries[it])) }
        )
        Text(
            when (cfg.tema) {
                TemaCartao.COLORIDO -> "Fundo verde, amarelo ou vermelho conforme a decisão. Decide de relance."
                TemaCartao.ESCURO -> "Fundo preto. Só a faixa de cima muda de cor."
                TemaCartao.CLARO -> "Fundo branco, para quem roda com a tela clara."
            },
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )

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
    }

    CardSecao(titulo = "Transparência e tempo") {
        Rotulo("Transparência: ${cfg.opacidade}%")
        Slider(
            value = cfg.opacidade.toFloat(),
            onValueChange = { mudar(cfg.copy(opacidade = it.toInt().coerceIn(40, 100))) },
            valueRange = 40f..100f
        )
        Text(
            "Abaixo de 100% dá para ler o endereço do app por baixo do cartão.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )

        Rotulo("Some sozinho em ${cfg.segundosNaTela}s")
        Slider(
            value = cfg.segundosNaTela.toFloat(),
            onValueChange = { mudar(cfg.copy(segundosNaTela = it.toInt().coerceIn(5, 60))) },
            valueRange = 5f..60f
        )
        Text(
            "Um toque no cartão fecha na hora, independente deste tempo.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }

    CardSecao(titulo = "Detalhes") {
        LinhaChave(
            titulo = "Faixa da decisão",
            detalhe = "BOA / ATENÇÃO / RUIM em letra grande no alto do cartão",
            marcado = cfg.avisoEmDestaque,
            onMudar = { mudar(cfg.copy(avisoEmDestaque = it)) }
        )
        LinhaChave(
            titulo = "Mostrar o valor da corrida na faixa",
            detalhe = "Ex.: \"BOA  •  R$ 18,40\"",
            marcado = cfg.mostrarApp,
            onMudar = { mudar(cfg.copy(mostrarApp = it)) }
        )
        OutlinedButton(
            onClick = { mudar(estilo.restaurarPadrao()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Voltar ao padrão") }
    }
}

/** O cartão desenhado como ele vai aparecer na rua. */
@Composable
private fun PreviaCartao(cfg: ConfigCartao, custoKmCentavos: Long) {
    val classe = Classificacao.BOA
    val corFaixa = when (classe) {
        Classificacao.BOA -> Lima
        Classificacao.MEDIA -> AmareloAlerta
        Classificacao.RUIM -> VermelhoPrejuizo
    }
    val fundoCartao = when (cfg.tema) {
        TemaCartao.COLORIDO -> Color(0xFF2E7D32)
        TemaCartao.ESCURO -> Color(0xFF14181C)
        TemaCartao.CLARO -> Color(0xFFF2F4F7)
    }
    val corTexto = if (cfg.tema == TemaCartao.CLARO) Color(0xFF101720) else Color.White
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

/** Linha de um campo: marca/desmarca e muda de lugar. */
@Composable
private fun LinhaCampo(
    campo: CampoCartao,
    marcado: Boolean,
    podeSubir: Boolean,
    podeDescer: Boolean,
    onMarcar: () -> Unit,
    onSubir: () -> Unit,
    onDescer: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onMarcar() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                .background(if (marcado) Lima else SuperficieAlta)
                .border(1.dp, if (marcado) Lima else Contorno, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (marcado) Text("✓", color = Fundo, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(campo.rotulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(campo.explicacao, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        }
        if (marcado) {
            SetaOrdem("▲", podeSubir, onSubir)
            SetaOrdem("▼", podeDescer, onDescer)
        }
    }
}

@Composable
private fun SetaOrdem(simbolo: String, ativa: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
            .background(SuperficieAlta)
            .clickable(enabled = ativa) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(simbolo, color = if (ativa) Lima else Contorno, fontSize = 12.sp)
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
