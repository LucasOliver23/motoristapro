package com.motoristapro.ui.componentes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario

/**
 * Estrutura padrão de cada aba: barra superior + conteúdo + FAB + snackbar.
 * Os insets inferiores ficam por conta da barra de abas (AppRaiz), por isso contentWindowInsets = 0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaAba(
    titulo: String,
    subtitulo: String? = null,
    snackbar: SnackbarHostState? = null,
    acoes: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    conteudo: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(titulo, fontWeight = FontWeight.Bold)
                        if (subtitulo != null) {
                            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        }
                    }
                },
                actions = acoes,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = fab,
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        content = conteudo
    )
}

/** Card de seção com título opcional. */
@Composable
fun CardSecao(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    destaque: Boolean = false,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (destaque) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (titulo != null) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (destaque) Lima else TextoSecundario,
                    fontWeight = FontWeight.SemiBold
                )
            }
            conteudo()
        }
    }
}

/** Número grande com rótulo e detalhe, para os cards de métricas. */
@Composable
fun Metrica(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    detalhe: String? = null,
    cor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rotulo, style = MaterialTheme.typography.labelMedium, color = TextoSecundario)
            Text(
                valor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detalhe != null) {
                Text(detalhe, style = MaterialTheme.typography.bodySmall, color = TextoSecundario, maxLines = 2)
            }
        }
    }
}

/** Linha "rótulo ........ valor". */
@Composable
fun LinhaValor(rotulo: String, valor: String, cor: Color = MaterialTheme.colorScheme.onSurface, negrito: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
        Text(
            valor,
            style = MaterialTheme.typography.bodyLarge,
            color = cor,
            fontWeight = if (negrito) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

/** Barra de progresso de meta com legenda. */
@Composable
fun ProgressoMeta(rotulo: String, atualCentavos: Long, metaCentavos: Long, textoAtual: String, textoMeta: String) {
    val progresso = if (metaCentavos > 0) (atualCentavos.toFloat() / metaCentavos).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rotulo, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (metaCentavos > 0) "${(progresso * 100).toInt()}%" else "sem meta",
                style = MaterialTheme.typography.bodyMedium,
                color = if (progresso >= 1f) Lima else TextoSecundario,
                fontWeight = FontWeight.SemiBold
            )
        }
        LinearProgressIndicator(
            progress = { progresso },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Lima,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Text("$textoAtual de $textoMeta", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
    }
}

/** Chips de seleção horizontal (período, categoria, plataforma...). */
@Composable
fun <T> ChipsSelecao(
    opcoes: List<T>,
    selecionado: T,
    rotulo: (T) -> String,
    onSelecionar: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        opcoes.forEach { op ->
            FilterChip(
                selected = op == selecionado,
                onClick = { onSelecionar(op) },
                label = { Text(rotulo(op)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lima,
                    selectedLabelColor = Color(0xFF0A0D0B)
                )
            )
        }
    }
}

/** Campo numérico/texto padrão dos formulários. */
@Composable
fun CampoFormulario(
    rotulo: String,
    valor: String,
    onValor: (String) -> Unit,
    modifier: Modifier = Modifier,
    numerico: Boolean = true,
    erro: Boolean = false,
    ajuda: String? = null,
    /** Texto fixo colado antes do valor, tipo "R$". */
    prefixo: String? = null,
    /**
     * Chamado quando o campo perde o foco, para devolver o texto arrumado
     * ("15000" -> "15.000,00"). Enquanto o motorista digita nada é mexido:
     * reformatar a cada tecla faz o cursor pular.
     */
    aoPerderFoco: ((String) -> String)? = null
) {
    val suporte: (@Composable () -> Unit)? = if (ajuda != null) {
        { Text(ajuda) }
    } else {
        null
    }
    var tinhaFoco by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(rotulo) },
        isError = erro,
        singleLine = true,
        supportingText = suporte,
        prefix = prefixo?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (numerico) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.fillMaxWidth().onFocusChanged { foco ->
            if (tinhaFoco && !foco.isFocused && aoPerderFoco != null && valor.isNotBlank()) {
                onValor(aoPerderFoco(valor))
            }
            tinhaFoco = foco.isFocused
        }
    )
}

/** Mensagem centralizada para listas vazias. */
@Composable
fun Vazio(texto: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(texto, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
    }
}
