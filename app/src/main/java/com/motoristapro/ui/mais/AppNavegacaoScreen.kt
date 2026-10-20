package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.service.AppNavegacao
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario

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
