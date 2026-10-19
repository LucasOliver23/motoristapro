package com.motoristapro.ui.mais

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.CartaoDestaque
import com.motoristapro.ui.componentes.CorSobreDestaque
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.SeloIcone
import com.motoristapro.ui.componentes.SeloTexto
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.AzulInfo
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Laranja
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.ModoTema
import com.motoristapro.ui.theme.PreferenciaTema
import com.motoristapro.ui.theme.Roxo
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import com.motoristapro.ui.theme.VermelhoPrejuizo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Escuro, Claro ou Do sistema, em três botões.
 *
 * Fica no alto do Menu, antes de qualquer ajuste: foi a opção que mais custou
 * para o motorista achar, e a que mais muda a cara do app.
 */
@Composable
internal fun SeletorDeTema(context: android.content.Context) {
    var modo by remember { mutableStateOf(PreferenciaTema.modo) }
    CardSecao(titulo = "APARÊNCIA") {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(SuperficieAlta).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ModoTema.entries.forEach { m ->
                val ativo = m == modo
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (ativo) MaterialTheme.colorScheme.background else Color.Transparent)
                        .clickable { PreferenciaTema.definir(context, m); modo = m }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.rotulo,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal,
                        color = if (ativo) Lima else TextoSecundario
                    )
                }
            }
        }
    }
}

/** Cabeçalho do "Mais": avatar, nome e um resumo de uma linha. */
@Composable
internal fun PerfilLinha(
    cfg: Configuracao,
    email: String?,
    nomeDaConta: String? = null,
    fotoDaConta: String? = null,
    selo: String? = null,
    seloAceso: Boolean = false,
    onClick: () -> Unit
) {
    // O nome digitado no app manda; sem ele, vale o da conta Google.
    val nome = cfg.nomeMotorista?.takeIf { it.isNotBlank() }
        ?: nomeDaConta?.takeIf { it.isNotBlank() } ?: "Motorista"
    CartaoDestaque(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar aceso, com a inicial: é a primeira coisa do Menu, como no desenho.
            FotoDaConta(fotoDaConta, nome.first().uppercase())
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                // O e-mail identifica a conta, o veículo identifica a conta de
                // custo. São coisas diferentes, por isso as duas linhas.
                if (!email.isNullOrBlank()) {
                    Text(email, style = MaterialTheme.typography.bodySmall, color = TextoSecundario, maxLines = 1)
                }
                Text(
                    cfg.veiculoNome?.takeIf { it.isNotBlank() }
                        ?: if (email.isNullOrBlank()) "Toque para completar seus dados" else "veículo não informado",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                    maxLines = 1
                )
            }
            if (selo != null) SeloTexto(selo, if (seloAceso) Lima else AmareloAlerta)
        }
    }
}

// ------------------------------------------------------------------ FERRAMENTAS

/**
 * Uma linha da lista de ajustes.
 *
 * Duas naturezas na mesma linha: quem tem [ligado] é interruptor (o toque liga
 * e desliga ali mesmo, sem abrir tela); quem não tem abre a sua tela. O subtítulo
 * sempre diz como a função está AGORA — é o que faz a lista valer mais que um menu.
 */
class Ferramenta(
    val titulo: String,
    val estado: String,
    val onClick: () -> Unit,
    /** O selo da esquerda. Um caractere, para o olho achar a linha sem ler. */
    val icone: String = "•",
    /** Interruptor: true/false. null = abre uma tela. */
    val ligado: Boolean? = null,
    /** Pinta o estado de verde (configurado / funcionando). */
    val aceso: Boolean = false,
    /** Pinta o estado de vermelho (precisa de atenção). */
    val alerta: Boolean = false,
    /** Mostra as três bolinhas do semáforo no canto. */
    val semaforo: Boolean = false
)

/**
 * A lista de ajustes de um grupo, dentro de um cartão só.
 *
 * Lista e não grade: o nome e o estado de cada função cabem inteiros na linha,
 * o interruptor fica onde o polegar já procura (na direita) e o olho desce a
 * coluna de selos em vez de pular em zigue-zague entre quadrados.
 */
@Composable
internal fun ListaFerramentas(titulo: String = "AJUSTES", itens: List<Ferramenta>) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelSmall,
            color = TextoSecundario,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Superficie)
        ) {
            Column(Modifier.fillMaxWidth()) {
                itens.forEachIndexed { indice, f ->
                    if (indice > 0) {
                        HorizontalDivider(
                            Modifier.padding(start = 60.dp),
                            thickness = 1.dp,
                            color = Contorno
                        )
                    }
                    LinhaFerramenta(f)
                }
            }
        }
    }
}

@Composable
private fun LinhaFerramenta(f: Ferramenta) {
    val ligadoAgora = f.ligado == true
    Row(
        Modifier.fillMaxWidth().clickable { f.onClick() }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val traco = iconeDeTraco(f.icone)
        if (traco != null) {
            SeloIcone(traco.first, traco.second)
        } else {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SuperficieAlta),
                contentAlignment = Alignment.Center
            ) {
                Text(f.icone, fontSize = 15.sp)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 10.dp)) {
            Text(
                f.titulo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                f.estado,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    f.alerta -> VermelhoPrejuizo
                    f.aceso || ligadoAgora -> Lima
                    else -> TextoSecundario
                }
            )
        }
        when {
            f.ligado != null -> Switch(
                checked = ligadoAgora,
                onCheckedChange = { f.onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF0D1117),
                    checkedTrackColor = Lima,
                    checkedBorderColor = Lima,
                    uncheckedThumbColor = TextoSecundario,
                    uncheckedTrackColor = SuperficieAlta,
                    uncheckedBorderColor = Contorno
                )
            )
            f.semaforo -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(VermelhoPrejuizo, AmareloAlerta, Lima).forEach { cor ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(cor))
                }
            }
            else -> Text("›", style = MaterialTheme.typography.titleMedium, color = TextoSecundario)
        }
    }
}

/**
 * O ícone de traço (e a cor do assunto) de cada ajuste, a partir do selo
 * antigo. Assim as linhas do Menu não mudam de lugar nem de texto — só o
 * desenho. Selo sem par aqui continua aparecendo como antes.
 */
private fun iconeDeTraco(selo: String): Pair<ImageVector, Color>? = when (selo) {
    "💰" -> Icones.Veiculo to AzulInfo
    "🚦" -> Icones.Semaforo to AmareloAlerta
    "🔧" -> Icones.Chave to Laranja
    "🔔" -> Icones.Sino to AmareloAlerta
    "👁" -> Icones.Olho to Lima
    "🩺" -> Icones.Checkup to Roxo
    "🔊" -> Icones.Som to Turquesa
    "⚪" -> Icones.Bolha to Roxo
    "⚠" -> Icones.Alerta to VermelhoPrejuizo
    "🧭" -> Icones.Bussola to AzulInfo
    "⭐" -> Icones.Estrela to AmareloAlerta
    "☁" -> Icones.Nuvem to Turquesa
    "📦" -> Icones.Caixa to Laranja
    "🌓" -> Icones.Contraste to Roxo
    "⚙" -> Icones.Engrenagem to TextoSecundario
    else -> null
}

/**
 * A foto da conta Google, redonda e com borda azul. Baixada uma vez e guardada
 * em cache; sem foto (login por e-mail, sem internet) fica a inicial do nome.
 */
@Composable
private fun FotoDaConta(url: String?, inicial: String) {
    val contexto = LocalContext.current
    var foto by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        foto = withContext(Dispatchers.IO) { baixarFoto(contexto, url) }
    }
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(Lima).border(2.dp, Lima, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = foto
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), contentDescription = "Foto da conta",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(inicial, color = CorSobreDestaque, style = EstiloValor, fontSize = 22.sp)
        }
    }
}

/** Baixa (ou lê do cache) a foto do perfil. Qualquer falha devolve null e fica a inicial. */
private fun baixarFoto(contexto: android.content.Context, url: String): android.graphics.Bitmap? = runCatching {
    val arquivo = java.io.File(contexto.cacheDir, "foto_conta_${url.hashCode()}.img")
    if (!arquivo.exists()) {
        val conexao = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conexao.responseCode !in 200..299) return null
            conexao.inputStream.use { entrada -> arquivo.outputStream().use { entrada.copyTo(it) } }
        } finally {
            conexao.disconnect()
        }
    }
    android.graphics.BitmapFactory.decodeFile(arquivo.path)
}.getOrNull()
