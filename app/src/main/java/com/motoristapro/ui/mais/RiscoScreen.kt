package com.motoristapro.ui.mais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.LinhaSwitch
import com.motoristapro.ui.componentes.TextoAjuda
import com.motoristapro.ui.theme.VermelhoPrejuizo

/** Palavras que acendem o aviso vermelho no cartão quando aparecem no embarque ou no destino. */
@Composable
internal fun CardSecaoEnderecosRisco(
    ativo: Boolean,
    mercados: Boolean,
    palavras: List<String>,
    onAtivo: (Boolean) -> Unit,
    onMercados: (Boolean) -> Unit,
    onAdicionar: (String) -> Unit,
    onRemover: (String) -> Unit
) {
    var nova by rememberSaveable { mutableStateOf("") }

    CardSecao(titulo = "Endereços de risco") {
        LinhaSwitch(
            "Alertar antes de aceitar",
            "Se o embarque ou o destino tiver uma das suas palavras, o cartão acende o aviso vermelho.",
            ativo, onAtivo
        )
        LinhaSwitch(
            "Avisar em mercado e atacadão",
            "Embarque em supermercado costuma dar espera longa e cancelamento. Reconhece as redes conhecidas.",
            mercados, onMercados
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CampoFormulario(
                "Bairro, rua ou região", nova, { nova = it },
                Modifier.weight(1f), numerico = false
            )
            OutlinedButton(
                onClick = {
                    onAdicionar(nova)
                    nova = ""
                },
                enabled = nova.isNotBlank()
            ) { Text("Add") }
        }

        if (palavras.isEmpty()) {
            TextoAjuda(
                "Nenhuma palavra cadastrada. Exemplos: um bairro que você evita à noite, " +
                    "uma rua sem saída, o nome de uma comunidade."
            )
        } else {
            palavras.forEach { p ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠  $p", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onRemover(p) }) {
                        Text("Remover", color = VermelhoPrejuizo)
                    }
                }
            }
        }
    }
}
