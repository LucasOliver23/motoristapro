package com.motoristapro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.corridas.CorridasRoute
import com.motoristapro.ui.dashboard.DashboardRoute
import com.motoristapro.ui.financas.FinancasRoute
import com.motoristapro.ui.mais.MaisRoute
import com.motoristapro.ui.relatorios.RelatoriosRoute
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * As cinco abas, com o selo que aparece na barra de baixo.
 *
 * Selo desenhado (emoji) e não ícone do Material de propósito: o conjunto do
 * Material que vem no app não tem bomba de combustível nem gráfico de barras, e
 * os parecidos que tem — carrinho de compras, calendário — dizem outra coisa.
 * Casa de verdade, relógio, dinheiro e gráfico são o que está no desenho.
 */
enum class Aba(val rotulo: String, val icone: String) {
    INICIO("Início", "🏠"),
    CORRIDAS("Corridas", "🕓"),
    FINANCAS("Finanças", "💰"),
    // "Atividade" em vez de "Relatórios": a aba responde "como foi meu período",
    // e relatório é palavra de escritório, não de quem está dirigindo.
    RELATORIOS("Atividade", "📊"),
    MAIS("Menu", "☰")
}

/** Estrutura principal: barra de abas embaixo e o conteúdo da aba selecionada. */
@Composable
fun AppRaiz() {
    var abaIndice by rememberSaveable { mutableIntStateOf(0) }
    val aba = Aba.entries[abaIndice]

    // O menu da bolha pede uma aba ao abrir o app. Zerado depois de atender,
    // senão o pedido voltaria a valer na próxima recomposição.
    LaunchedEffect(Unit) {
        NavegacaoRapida.abaPedida.collect { pedido ->
            if (pedido != null && pedido in Aba.entries.indices) {
                abaIndice = pedido
                NavegacaoRapida.abaPedida.value = null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // A barra usa o MESMO fundo da tela e sem a pílula de seleção do
            // Material: o que marca a aba aberta é o ícone ficar verde, não uma
            // cápsula colorida atrás dele.
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                Aba.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == aba,
                        onClick = { abaIndice = item.ordinal },
                        // O emoji tem cor própria, então a aba fechada se apaga
                        // pela transparência — é o que o desenho faz.
                        icon = {
                            Text(
                                item.icone,
                                fontSize = 17.sp,
                                modifier = Modifier.alpha(if (item == aba) 1f else 0.45f)
                            )
                        },
                        label = { Text(item.rotulo) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Lima,
                            selectedTextColor = Lima,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = TextoSecundario,
                            unselectedTextColor = TextoSecundario
                        )
                    )
                }
            }
        }
    ) { padding ->
        // consumeWindowInsets: avisa os filhos que o espaco da barra de abas ja
        // foi descontado. Sem isso, uma tela cheia com imePadding() somava a
        // altura da barra ao recuo do teclado e deixava um buraco embaixo.
        Box(Modifier.fillMaxSize().consumeWindowInsets(padding).padding(padding)) {
            when (aba) {
                Aba.INICIO -> DashboardRoute(irPara = { abaIndice = it.ordinal })
                Aba.CORRIDAS -> CorridasRoute()
                Aba.FINANCAS -> FinancasRoute()
                Aba.RELATORIOS -> RelatoriosRoute()
                Aba.MAIS -> MaisRoute()
            }
        }
    }
}
