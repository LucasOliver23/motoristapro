package com.motoristapro.ui.mais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.atualizacao.EstadoAtualizacao
import com.motoristapro.nuvem.EstadoNuvem
import com.motoristapro.ui.componentes.BotaoLima
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.componentes.TextoAjuda
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private fun dataHora(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(FORMATO_DATA_HORA)

/** Sub-tela "Conta e nuvem": último envio, enviar/baixar, sair e começar do zero. */
@Composable
internal fun NuvemConteudo(
    nuvem: EstadoNuvem,
    onEnviar: () -> Unit,
    onBaixar: () -> Unit,
    onSair: () -> Unit,
    onZerar: () -> Unit
) {
    CardSecao(titulo = "Nuvem") {
        LinhaValor(
            "Último backup",
            if (nuvem.atualizadoNaNuvemEm > 0) dataHora(nuvem.atualizadoNaNuvemEm) else "ainda não enviado"
        )
        if (nuvem.corridasNaNuvem >= 0) LinhaValor("Corridas na nuvem", "${nuvem.corridasNaNuvem}")
        TextoAjuda("Os dados sobem automaticamente ao entrar. Use \"Baixar da nuvem\" ao trocar de celular.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoLima(
                onClick = onEnviar,
                enabled = !nuvem.ocupado,
                modifier = Modifier.weight(1f)
            ) { Text(if (nuvem.ocupado) "Aguarde..." else "Enviar agora") }
            OutlinedButton(
                onClick = onBaixar,
                enabled = !nuvem.ocupado,
                modifier = Modifier.weight(1f)
            ) { Text("Baixar da nuvem") }
        }
        OutlinedButton(onClick = onSair, modifier = Modifier.fillMaxWidth()) {
            Text("Sair da conta", color = VermelhoPrejuizo)
        }
        HorizontalDivider(color = Contorno)
        TextoAjuda(
            "Entrou com outra conta neste celular e os dados do motorista anterior " +
                "ficaram? A partir de agora o app zera sozinho quando a conta muda — " +
                "e aqui você limpa o que já estava."
        )
        OutlinedButton(
            onClick = onZerar,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Começar do zero neste celular", color = VermelhoPrejuizo) }
    }
}

/** Sub-tela "Backup e exportação": backup completo, restaurar e as planilhas CSV. */
@Composable
internal fun BackupConteudo(
    ocupado: Boolean,
    onBackup: () -> Unit,
    onRestaurar: () -> Unit,
    onCsvCorridas: () -> Unit,
    onCsvDespesas: () -> Unit,
    onCsvOfertas: () -> Unit
) {
    CardSecao(titulo = "Backup e exportação") {
        TextoAjuda(
            "Seus dados ficam só neste celular. Faça backup de vez em quando e salve no Google Drive " +
                "(escolha \"Drive\" na tela que abrir)."
        )
        BotaoLima(
            onClick = onBackup,
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (ocupado) "Aguarde..." else "Fazer backup completo", fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onRestaurar, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
            Text("Restaurar backup")
        }
        Text("Exportar para Excel (CSV)", style = MaterialTheme.typography.labelLarge, color = TextoSecundario)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCsvCorridas, enabled = !ocupado,
                modifier = Modifier.weight(1f)
            ) { Text("Corridas") }
            OutlinedButton(
                onClick = onCsvDespesas, enabled = !ocupado,
                modifier = Modifier.weight(1f)
            ) { Text("Despesas") }
            OutlinedButton(
                onClick = onCsvOfertas, enabled = !ocupado,
                modifier = Modifier.weight(1f)
            ) { Text("Ofertas") }
        }
    }
}

/** Versão instalada e atualização do app sem passar pelo Android Studio. */
@Composable
internal fun AtualizacaoCard(
    estado: EstadoAtualizacao,
    versao: String,
    codigo: Int,
    onProcurar: () -> Unit,
    onInstalar: (com.motoristapro.atualizacao.VersaoPublicada) -> Unit
) {
    CardSecao(titulo = "Versão do app") {
        LinhaValor("Instalada", "$versao (build $codigo)")
        val nova = estado.disponivel
        if (nova != null) {
            Text(
                "Nova versão disponível: ${nova.versionName}",
                color = Lima, fontWeight = FontWeight.Bold
            )
            if (nova.notas.isNotBlank()) {
                TextoAjuda(nova.notas)
            }
            if (estado.baixando) {
                LinearProgressIndicator(
                    progress = { estado.progresso / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Lima
                )
                TextoAjuda("Baixando... ${estado.progresso}%")
            } else {
                BotaoLima(
                    onClick = { onInstalar(nova) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Baixar e instalar", fontWeight = FontWeight.Bold) }
            }
        } else {
            OutlinedButton(
                onClick = onProcurar,
                enabled = !estado.verificando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.verificando) "Procurando..." else "Procurar atualização") }
        }
    }
}
