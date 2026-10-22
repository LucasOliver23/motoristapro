package com.motoristapro.ui.componentes

import com.motoristapro.ui.theme.VerdeLucro
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.SuperficieMaisAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.TextoSecundario

/*
 * Os blocos do visual novo (4.0), os mesmos das telas desenhadas em mockups/:
 * cartão de destaque com brilho azul no canto, rótulo pequeno em caixa alta,
 * número grande com os centavos apagados, pílulas e segmentado.
 * Cada tela monta o seu layout com estas peças — assim as cinco abas falam a
 * mesma língua e uma mudança de estilo é feita num lugar só.
 */

/** O cartão principal de cada tela: o número que importa, com um brilho azul em cima. */
@Composable
fun CartaoDestaque(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Superficie)
            .background(Brush.linearGradient(listOf(Lima.copy(alpha = 0.10f), Color.Transparent)))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = conteudo
    )
}

/** Cartão comum do visual novo: fundo do cartão, canto 20, sem contorno. */
@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    preenchimento: Int = 14,
    espaco: Int = 8,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie)
            .padding(preenchimento.dp),
        verticalArrangement = Arrangement.spacedBy(espaco.dp),
        content = conteudo
    )
}

/** "LUCRO DO DIA": caixa alta, pequeno e espaçado. Diz o que é sem disputar com o número. */
@Composable
fun Rotulo(texto: String, modifier: Modifier = Modifier, cor: Color = TextoSecundario) {
    Text(
        texto.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = cor,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        maxLines = 1
    )
}

/**
 * "R$ 142,80" com os centavos menores e apagados: o olho pega os reais primeiro.
 * [texto] é o que sai de emReais(); sem vírgula, sai inteiro.
 */
@Composable
fun ValorComCentavos(
    texto: String,
    tamanho: TextUnit,
    modifier: Modifier = Modifier,
    cor: Color = TextoPrincipal
) {
    val virgula = texto.lastIndexOf(',')
    Text(
        buildAnnotatedString {
            if (virgula < 0) {
                append(texto)
            } else {
                append(texto.substring(0, virgula))
                withStyle(SpanStyle(fontSize = tamanho * 0.6f, color = TextoSecundario)) {
                    append(texto.substring(virgula))
                }
            }
        },
        modifier = modifier,
        style = EstiloValor,
        fontSize = tamanho,
        color = cor,
        maxLines = 1
    )
}

/** Barra da meta: trilho cinza, progresso azul, canto redondo. [fracao] vai de 0 a 1. */
@Composable
fun BarraMeta(fracao: Float, modifier: Modifier = Modifier, cor: Color = VerdeLucro, altura: Int = 8) {
    Box(
        modifier.fillMaxWidth().height(altura.dp).clip(RoundedCornerShape(99.dp)).background(SuperficieMaisAlta)
    ) {
        Box(
            Modifier.fillMaxWidth(fracao.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(99.dp)).background(cor)
        )
    }
}

/** Quadradinho de número: rótulo em cima, valor embaixo. Os "4 números" do Início. */
@Composable
fun MetricaCompacta(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    cor: Color = TextoPrincipal
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Superficie).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Rotulo(rotulo)
        Text(
            valor,
            style = EstiloValor,
            fontSize = 19.sp,
            color = cor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Número centralizado num quadradinho: "ACEITAS 48". */
@Composable
fun NumeroCentral(rotulo: String, valor: String, modifier: Modifier = Modifier, cor: Color = TextoPrincipal) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Superficie).padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Rotulo(rotulo)
        Text(valor, style = EstiloValor, fontSize = 20.sp, color = cor, maxLines = 1)
    }
}

/** Pílula de filtro ("Todas", "Aceitas"): azul quando escolhida, cinza quando não. */
@Composable
fun Pilula(texto: String, selecionada: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(99.dp))
            .background(if (selecionada) Lima else SuperficieAlta)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selecionada) CorSobreDestaque else TextoSecundario,
            maxLines = 1
        )
    }
}

/** Pílula de dia da fita: "SEG" em cima, "9" embaixo. */
@Composable
fun PilulaDia(cima: String, baixo: String, selecionada: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selecionada) Lima else SuperficieAlta)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            cima, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (selecionada) CorSobreDestaque else TextoSecundario, maxLines = 1
        )
        Text(
            baixo, style = EstiloValor, fontSize = 17.sp,
            color = if (selecionada) CorSobreDestaque else TextoPrincipal, maxLines = 1
        )
    }
}

/** Segmentado "Dia | Semana | Mês | Ano": uma barra com a opção escolhida acesa. */
@Composable
fun Segmentado(opcoes: List<String>, selecionado: Int, modifier: Modifier = Modifier, onSelecionar: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(4.dp)
    ) {
        opcoes.forEachIndexed { i, texto ->
            val sel = i == selecionado
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (sel) SuperficieMaisAlta else Color.Transparent)
                    .clickable { onSelecionar(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    texto,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sel) TextoPrincipal else TextoSecundario,
                    maxLines = 1
                )
            }
        }
    }
}

/** Selo pequeno colorido: "BOA", "PRO", "+14% vs anterior". */
@Composable
fun SeloTexto(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(cor.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = cor,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1
    )
}

/** Linha fina entre os itens de uma lista dentro do mesmo cartão. */
@Composable
fun DivisorLista(recuo: Int = 0) {
    HorizontalDivider(Modifier.padding(start = recuo.dp), thickness = 1.dp, color = Contorno)
}

/** Texto escuro para usar SOBRE o azul (botão, pílula escolhida): o branco não lê ali. */
val CorSobreDestaque: Color get() = com.motoristapro.ui.theme.SobreDestaque
