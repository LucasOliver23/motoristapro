package com.motoristapro.ui.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.R

/**
 * O selo da plataforma: o logo oficial quando existe, a inicial colorida quando não.
 *
 * O desenho é o mesmo nos dois casos — quadrado de canto arredondado, do tamanho
 * do ícone que cada app usa. Assim a lista não muda de forma se um dia faltar
 * arquivo, e trocar um logo é trocar um PNG em res/drawable-nodpi.
 *
 * As cores de reserva são as das próprias marcas, pelo motivo prático de serem
 * o que o motorista reconhece de relance na lista — amarelo é 99, verde-limão é
 * inDrive — e não para imitar logo nenhum.
 */
private data class Marca(val arte: Int?, val fundo: Color, val tinta: Color, val sigla: String)

private fun marcaDe(nomeOuPacote: String?): Marca {
    val chave = (nomeOuPacote ?: "").lowercase()
    return when {
        chave.contains("uber") -> Marca(R.drawable.logo_uber, Color(0xFF0F0F0F), Color.White, "U")
        chave.contains("99") -> Marca(R.drawable.logo_99, Color(0xFFFFD400), Color(0xFF0F0F0F), "99")
        chave.contains("ifood") -> Marca(R.drawable.logo_ifood, Color(0xFFEA1D2C), Color.White, "iF")
        chave.contains("indrive") || chave.contains("indriver") ->
            Marca(R.drawable.logo_indrive, Color(0xFFC6F432), Color(0xFF0F0F0F), "iD")
        else -> Marca(null, Color(0xFF64748B), Color.White, chave.take(2).uppercase().ifBlank { "—" })
    }
}

@Composable
fun LogoPlataforma(
    plataforma: String?,
    modifier: Modifier = Modifier,
    tamanho: Dp = 34.dp
) {
    val marca = marcaDe(plataforma)
    val forma = RoundedCornerShape(tamanho / 3.4f)
    if (marca.arte != null) {
        Image(
            painter = painterResource(marca.arte),
            contentDescription = plataforma,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(tamanho).clip(forma)
        )
    } else {
        Box(
            modifier.size(tamanho).clip(forma).background(marca.fundo),
            contentAlignment = Alignment.Center
        ) {
            Text(
                marca.sigla,
                color = marca.tinta,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = (tamanho.value / 2.8f).sp
            )
        }
    }
}
