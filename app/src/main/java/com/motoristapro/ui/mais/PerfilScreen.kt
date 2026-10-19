package com.motoristapro.ui.mais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.ui.componentes.BotaoLima
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.TextoAjuda
import com.motoristapro.ui.theme.Lima

/** Perfil do motorista: nome, telefone, cidade e a conta usada para entrar. */
@Composable
internal fun PerfilCard(
    cfg: Configuracao,
    emailConta: String?,
    onSalvar: (String, String, String) -> Unit
) {
    var nome by rememberSaveable(cfg.id) { mutableStateOf(cfg.nomeMotorista.orEmpty()) }
    var telefone by rememberSaveable(cfg.id) { mutableStateOf(cfg.telefone.orEmpty()) }
    var cidade by rememberSaveable(cfg.id) { mutableStateOf(cfg.cidade.orEmpty()) }

    val mudou = nome != cfg.nomeMotorista.orEmpty() ||
        telefone != cfg.telefone.orEmpty() ||
        cidade != cfg.cidade.orEmpty()

    // Salva sozinho ao sair da tela. Antes, quem digitava o nome e tocava em
    // voltar perdia tudo: o botão "Salvar perfil" nasce embaixo dos campos e
    // nem sempre está à vista quando o teclado está aberto.
    val pendente by rememberUpdatedState(Triple(nome, telefone, cidade) to mudou)
    DisposableEffect(Unit) {
        onDispose {
            val (dados, precisa) = pendente
            if (precisa) onSalvar(dados.first, dados.second, dados.third)
        }
    }

    CardSecao(titulo = "Meu perfil", destaque = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Inicial do nome (ou do email) como avatar.
            val inicial = (nome.ifBlank { emailConta.orEmpty() }).trim().firstOrNull()?.uppercase() ?: "M"
            Surface(shape = CircleShape, color = Lima, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(inicial, color = Color(0xFF0A0D0B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    nome.ifBlank { "Motorista" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextoAjuda(emailConta ?: "sem conta conectada")
            }
        }
        CampoFormulario("Nome", nome, { nome = it }, numerico = false)
        CampoFormulario("Telefone", telefone, { telefone = it }, numerico = false)
        CampoFormulario("Cidade", cidade, { cidade = it }, numerico = false)
        if (mudou) {
            BotaoLima(
                onClick = { onSalvar(nome, telefone, cidade) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salvar perfil", fontWeight = FontWeight.Bold) }
        }
    }
}
