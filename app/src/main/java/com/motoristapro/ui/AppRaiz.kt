package com.motoristapro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.motoristapro.ui.corridas.CorridasRoute
import com.motoristapro.ui.dashboard.DashboardRoute
import com.motoristapro.ui.financas.FinancasRoute
import com.motoristapro.ui.mais.MaisRoute
import com.motoristapro.ui.relatorios.RelatoriosRoute
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class Aba(val rotulo: String, val icone: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    CORRIDAS("Corridas", Icons.Filled.List),
    FINANCAS("Finanças", Icons.Filled.ShoppingCart),
    RELATORIOS("Relatórios", Icons.Filled.DateRange),
    MAIS("Mais", Icons.Filled.Menu)
}

/** Estrutura principal: barra de abas embaixo e o conteúdo da aba selecionada. */
@Composable
fun AppRaiz() {
    var abaIndice by rememberSaveable { mutableIntStateOf(0) }
    val aba = Aba.entries[abaIndice]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Aba.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == aba,
                        onClick = { abaIndice = item.ordinal },
                        icon = { Icon(item.icone, contentDescription = item.rotulo) },
                        label = { Text(item.rotulo) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0A0D0B),
                            selectedTextColor = Lima,
                            indicatorColor = Lima,
                            unselectedIconColor = TextoSecundario,
                            unselectedTextColor = TextoSecundario
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
