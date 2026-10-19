package com.motoristapro.ui.componentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario

/** Texto miúdo e apagado que explica o que está em volta. */
@Composable
fun TextoAjuda(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
}

/** Interruptor com título e uma linha de explicação embaixo. */
@Composable
fun LinhaSwitch(titulo: String, descricao: String, marcado: Boolean, onMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titulo, fontWeight = FontWeight.SemiBold)
            TextoAjuda(descricao)
        }
        Switch(
            checked = marcado,
            onCheckedChange = onMudar,
            colors = SwitchDefaults.colors(checkedTrackColor = Lima, checkedThumbColor = Color(0xFF0A0D0B))
        )
    }
}

/** Botão cheio em Lima com texto escuro, no tamanho padrão do Material. */
@Composable
fun BotaoLima(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B)),
        content = content
    )
}
