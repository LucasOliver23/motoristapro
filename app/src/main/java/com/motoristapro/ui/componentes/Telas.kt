package com.motoristapro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Uma tela inteira desenhada por cima da aba.
 *
 * Antes isto era uma Dialog, e a janela dela media mais que a tela do celular:
 * o rodape (o botao "Continuar") ficava fora da borda de baixo. Aqui dentro da
 * propria janela do app, os recuos de status bar e teclado chegam certos.
 *
 * O clickable sem efeito visual existe só para a camada engolir os toques —
 * sem ele o motorista acertaria os botoes da tela que ficou atras.
 */
@Composable
fun CamadaTelaCheia(conteudo: @Composable () -> Unit) {
    val semRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = semRipple, indication = null) { }
    ) {
        conteudo()
    }
}

/** Moldura das sub-telas: barra com título e voltar, conteúdo rolável embaixo. */
@Composable
fun SubTelaHost(
    titulo: String,
    onFechar: () -> Unit,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Column(
        // statusBarsPadding (e nao safeDrawingPadding): a barra de abas do app ja
        // fica embaixo desta camada, entao o recuo de baixo quem da e o teclado.
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFechar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = conteudo
        )
        Spacer(Modifier.height(12.dp))
    }
}
