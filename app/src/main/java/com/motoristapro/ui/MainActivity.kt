package com.motoristapro.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motoristapro.MotoristaApp
import com.motoristapro.auth.LoginScreen
import com.motoristapro.service.leitorOfertasAtivo
import com.motoristapro.ui.theme.MotoristaTema
import com.motoristapro.ui.theme.VerdeLucro
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Ponto de entrada. Antes do Dashboard, garante as duas permissões:
 *  1. Sobreposição de tela  (Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
 *  2. Serviço de Acessibilidade (Settings.ACTION_ACCESSIBILITY_SETTINGS)
 *
 * As duas são concedidas em telas do sistema; ao voltar para o app (onResume)
 * o estado é rechecado e, com tudo liberado, o Dashboard abre sozinho.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema sempre escuro: ícones claros na status bar e na barra de navegação.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            MotoristaTema {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppComLogin()
                }
            }
        }
    }
}

/** Estado das permissões lido do sistema. */
data class EstadoPermissoes(val sobreposicao: Boolean, val acessibilidade: Boolean) {
    val tudoLiberado: Boolean get() = sobreposicao && acessibilidade
}

fun Context.lerPermissoes() = EstadoPermissoes(
    sobreposicao = Settings.canDrawOverlays(this),
    acessibilidade = leitorOfertasAtivo()
)

/** Login (quando o Firebase está configurado) -> permissões -> app. */
@Composable
private fun AppComLogin() {
    val context = LocalContext.current
    val app = context.applicationContext as MotoristaApp
    val autenticacao = app.autenticacao

    if (!autenticacao.disponivel) {
        AppComPermissoes()
        return
    }

    val usuario by autenticacao.usuario.collectAsStateWithLifecycle()

    if (usuario == null) {
        LoginScreen(autenticacao = autenticacao, onEntrou = { })
        return
    }

    // Logou: acerta os dados com a nuvem uma vez por sessão.
    LaunchedEffect(usuario?.uid) {
        app.nuvem.sincronizarAoEntrar()
    }
    AppComPermissoes()
}

@Composable
private fun AppComPermissoes() {
    val context = LocalContext.current
    var estado by remember { mutableStateOf(context.lerPermissoes()) }

    // Rechecado toda vez que o usuário volta das Configurações do Android.
    LifecycleResumeEffect(Unit) {
        estado = context.lerPermissoes()
        onPauseOrDispose { }
    }

    if (estado.tudoLiberado) {
        AppRaiz()
    } else {
        PermissoesScreen(
            estado = estado,
            onPedirSobreposicao = { context.abrirPermissaoSobreposicao() },
            onPedirAcessibilidade = { context.abrirConfigAcessibilidade() },
            onConfigRestrita = { context.abrirDetalhesDoApp() }
        )
    }
}

@Composable
fun PermissoesScreen(
    estado: EstadoPermissoes,
    onPedirSobreposicao: () -> Unit,
    onPedirAcessibilidade: () -> Unit,
    onConfigRestrita: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Bem-vindo ao MotoristaPro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Para calcular R$/km e R$/hora de cada oferta automaticamente, o app precisa de duas permissões.",
            style = MaterialTheme.typography.bodyLarge
        )

        PassoPermissao(
            numero = 1,
            titulo = "Aparecer sobre outros apps",
            descricao = "Permite mostrar o resultado da oferta numa janela por cima da Uber e da 99.",
            concedida = estado.sobreposicao,
            rotuloBotao = "Permitir sobreposição",
            onClick = onPedirSobreposicao
        )

        // Divulgação em destaque exigida pela política de Acessibilidade do Google Play.
        PassoPermissao(
            numero = 2,
            titulo = "Leitor de ofertas (Acessibilidade)",
            descricao = "O MotoristaPro lê apenas o valor, a distância e o tempo das ofertas exibidas " +
                "nos apps Uber Driver, 99 Motorista e iFood para Entregadores. Nenhum dado sai do seu celular. " +
                "Em Acessibilidade, abra \"Apps instalados\" e ligue \"MotoristaPro – Leitor de ofertas\".",
            concedida = estado.acessibilidade,
            rotuloBotao = "Abrir Acessibilidade",
            onClick = onPedirAcessibilidade
        )

        if (!estado.acessibilidade && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Text("Apareceu \"Configuração restrita\"?", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Acontece com apps instalados fora da Play Store. Toque abaixo, abra o menu ⋮ " +
                            "no canto superior e escolha \"Permitir configurações restritas\". Depois ative de novo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onConfigRestrita) { Text("Abrir informações do app") }
                }
            }
        }
    }
}

@Composable
private fun PassoPermissao(
    numero: Int,
    titulo: String,
    descricao: String,
    concedida: Boolean,
    rotuloBotao: String,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (concedida) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Concedida", tint = VerdeLucro)
                } else {
                    Text("$numero.", fontWeight = FontWeight.Bold)
                }
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(descricao, style = MaterialTheme.typography.bodyMedium)
            if (!concedida) {
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(rotuloBotao) }
            } else {
                Text("Concedida ✓", color = VerdeLucro, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ------------------------------------------------------------------ intents do sistema

/** Tela "Aparecer sobre outros apps" já apontando para o MotoristaPro. */
fun Context.abrirPermissaoSobreposicao() {
    val direto = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
    abrirComFallback(direto, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
}

fun Context.abrirConfigAcessibilidade() {
    abrirComFallback(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
}

fun Context.abrirDetalhesDoApp() {
    abrirComFallback(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_SETTINGS)
    )
}

/** Alguns fabricantes removem telas de configuração: tenta a principal e cai para a genérica. */
private fun Context.abrirComFallback(principal: Intent, alternativa: Intent) {
    try {
        startActivity(principal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        runCatching { startActivity(alternativa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
