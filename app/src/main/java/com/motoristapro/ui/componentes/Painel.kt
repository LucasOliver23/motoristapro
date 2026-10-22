package com.motoristapro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.theme.SuperficieMaisAlta
import com.motoristapro.ui.theme.TextoPrincipal

/*
 * As peças do "painel de instrumento" (5.1): cores sólidas sobre preto, sem
 * degradê e sem sombra. A profundidade dos botões é só uma faixa mais escura
 * embaixo, como uma tecla.
 */

/**
 * Botão tátil: cor sólida, cantos levemente arredondados, faixa de 4 dp mais
 * escura embaixo e no mínimo 48 dp de altura (o dedo acerta com o carro andando).
 */
@Composable
fun Tecla(
    texto: String,
    fundo: Color,
    corTexto: Color,
    borda: Color,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    altura: Dp = 52.dp,
    tamanhoTexto: Int = 15,
    onClick: () -> Unit
) {
    // A largura vem do TEXTO (IntrinsicSize.Max), a não ser que quem chama peça
    // fillMaxWidth. Sem isto, dentro de uma linha o botão engolia a largura toda
    // e espremia o texto ao lado até sumir (5.1.1, a linha do Copiloto).
    Box(
        modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp).height(altura)
            .clip(RoundedCornerShape(12.dp)).background(borda)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.fillMaxWidth().height(altura - 4.dp).clip(RoundedCornerShape(12.dp)).background(fundo)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icone != null) Icon(icone, contentDescription = null, tint = corTexto, modifier = Modifier.size(20.dp))
            Text(texto, color = corTexto, fontSize = tamanhoTexto.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

/** A barra em segmentos: acesos até [fracao], apagados (#2C2C2E) no resto. */
@Composable
fun BarraSegmentos(fracao: Float, cor: Color, modifier: Modifier = Modifier, segmentos: Int = 20) {
    val acesos = (fracao.coerceIn(0f, 1f) * segmentos).toInt()
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segmentos) { i ->
            Box(
                Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < acesos) cor else SuperficieMaisAlta)
            )
        }
    }
}

/** Selo de estado: bolinha e contorno na cor do significado, texto branco, fundo preto. */
@Composable
fun SeloEstado(texto: String, cor: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.clip(RoundedCornerShape(99.dp)).border(1.5.dp, cor, RoundedCornerShape(99.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
        Text(texto, color = TextoPrincipal, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}
