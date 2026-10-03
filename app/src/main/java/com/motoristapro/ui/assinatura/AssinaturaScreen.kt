package com.motoristapro.ui.assinatura

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import com.motoristapro.assinatura.Acesso
import com.motoristapro.assinatura.AssinaturaManager
import com.motoristapro.assinatura.Plano
import com.motoristapro.assinatura.PrecoPlano
import com.motoristapro.assinatura.SituacaoAcesso
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo

/** O que o motorista ganha pagando — escrito como ele fala, não como o app funciona. */
private val VANTAGENS = listOf(
    "Lê a oferta na tela e diz na hora se vale a pena" to
        "R$/km, R$/hora e o lucro de verdade, antes de você aceitar",
    "Seu custo real por km" to
        "Combustível, pneu, óleo, seguro, IPVA e depreciação na conta de cada corrida",
    "Melhores horários" to
        "Em que faixa de 2 h o aplicativo costuma mandar corrida boa, pelo seu histórico",
    "Aviso por voz e bolha flutuante" to
        "Decide de ouvido, sem tirar os olhos do trânsito",
    "Backup na nuvem" to
        "Trocou de celular, seus dados vão junto"
)

/**
 * Tela de assinatura: aparece quando o teste acaba (e trava o resto do app) ou
 * quando o motorista toca em "Assinar" por vontade própria.
 *
 * O pagamento acontece FORA do app, no navegador: o app sai pela Play Store por
 * APK, e a cobrança do Google só funciona para quem instalou pela loja. Quando o
 * Mercado Pago confirma, a função do servidor grava a validade e esta tela
 * destrava sozinha — por isso ela reconfere ao voltar do navegador.
 */
@Composable
fun AssinaturaScreen(
    acesso: Acesso,
    precos: List<PrecoPlano>,
    gerente: AssinaturaManager,
    onFechar: (() -> Unit)? = null,
    onSair: () -> Unit
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var escolhido by remember { mutableStateOf(Plano.TRIMESTRAL) }
    var semLink by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    var conferindo by remember { mutableStateOf(false) }
    var recado by remember { mutableStateOf<String?>(null) }

    // Voltou do navegador depois de pagar: relê o documento em vez de esperar.
    LifecycleResumeEffect(Unit) {
        gerente.reconferir()
        onPauseOrDispose { }
    }

    Column(
        Modifier.fillMaxSize().background(Fundo).safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Cabecalho(acesso)

        VANTAGENS.forEach { (titulo, detalhe) ->
            Row(verticalAlignment = Alignment.Top) {
                Text("✓", color = Lima, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                Column {
                    Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(detalhe, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        precos.sortedBy { it.plano.meses }.forEach { preco ->
            CartaoPlano(
                preco = preco,
                escolhido = preco.plano == escolhido,
                economia = economiaPercentual(precos, preco),
                onEscolher = { escolhido = preco.plano }
            )
        }

        // O link vem do SERVIDOR a cada toque, com o id da conta grudado no
        // pagamento. Link fixo guardado na nuvem não serve: o Mercado Pago não
        // garante que um parâmetro posto na URL chegue no pagamento, e pagamento
        // sem id da conta é pagamento que ninguém sabe de quem é.
        Button(
            onClick = {
                if (ocupado) return@Button
                ocupado = true
                semLink = false
                escopo.launch {
                    val link = gerente.criarCheckout(escolhido)
                    ocupado = false
                    if (link.isNullOrBlank()) semLink = true
                    else context.abrirNoNavegador(link)
                }
            },
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Fundo)
        ) {
            Text(
                if (ocupado) "Abrindo o pagamento..." else "Assinar ${escolhido.rotulo.lowercase()}",
                fontWeight = FontWeight.Bold
            )
        }

        if (semLink) {
            Text(
                "Não consegui abrir o pagamento agora. Confira sua internet e tente de novo — " +
                    "se continuar, fale com o suporte do app.",
                style = MaterialTheme.typography.bodySmall, color = VermelhoPrejuizo
            )
        }

        Text(
            "O pagamento é feito no site do Mercado Pago, fora do app — dá para pagar por Pix, " +
                "cartão ou boleto. Assim que ele confirmar, esta tela libera sozinha; " +
                "pelo Pix costuma ser na hora.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )

        OutlinedButton(
            onClick = {
                if (conferindo) return@OutlinedButton
                conferindo = true
                recado = null
                escopo.launch {
                    val liberado = gerente.conferirNoServidor()
                    conferindo = false
                    // Só avisa quando NÃO achou: quando acha, a tela destrava
                    // sozinha e o aviso seria conversa fiada em cima do óbvio.
                    if (!liberado) recado = "Ainda não achei seu pagamento. " +
                        "Se acabou de pagar por boleto, pode levar até 2 dias úteis."
                }
            },
            enabled = !conferindo,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (conferindo) "Conferindo..." else "Já paguei — conferir agora")
        }

        recado?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = AmareloAlerta)
        }

        if (onFechar != null) {
            TextButton(onClick = onFechar, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
        }
        TextButton(onClick = onSair, modifier = Modifier.fillMaxWidth()) {
            Text("Sair da conta", color = TextoSecundario)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Cabecalho(acesso: Acesso) {
    val (titulo, texto, cor) = when (acesso.situacao) {
        SituacaoAcesso.TESTE_ATIVO -> Triple(
            "Teste grátis",
            "Faltam ${acesso.diasDeTeste} dia(s). Assine antes de acabar e não perde nada do que já juntou.",
            Lima
        )
        SituacaoAcesso.TESTE_ACABOU -> Triple(
            "Seu teste acabou",
            "Foram ${Acesso.DIAS_DE_TESTE} dias. Seus dados continuam guardados — assine e o app volta de onde parou.",
            AmareloAlerta
        )
        SituacaoAcesso.ASSINATURA_VENCIDA -> Triple(
            "Assinatura vencida",
            "O pagamento não entrou este mês. Renove e o app destrava na hora.",
            VermelhoPrejuizo
        )
        SituacaoAcesso.ASSINATURA_ATIVA -> Triple(
            "Assinatura ativa",
            "Vence em ${acesso.diasDeAssinatura} dia(s). Dá para trocar de plano aqui.",
            Lima
        )
        else -> Triple("MotoristaPro", "Escolha um plano para continuar.", Lima)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = cor)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
    }
}

@Composable
private fun CartaoPlano(
    preco: PrecoPlano,
    escolhido: Boolean,
    economia: Int,
    onEscolher: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (escolhido) MaterialTheme.colorScheme.primaryContainer else SuperficieAlta)
            .border(
                width = if (escolhido) 2.dp else 1.dp,
                color = if (escolhido) Lima else Contorno,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onEscolher() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preco.plano.rotulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (economia > 0) {
                    Box(
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp))
                            .background(Lima).padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "-$economia%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Fundo,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                if (preco.plano.meses > 1) "${preco.porMesCentavos.emReais()} por mês"
                else "cobrado todo mês",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        Text(preco.centavos.emReais(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Quanto este plano economiza por mês contra o mais curto. */
private fun economiaPercentual(precos: List<PrecoPlano>, preco: PrecoPlano): Int {
    val base = precos.firstOrNull { it.plano == Plano.MENSAL }?.porMesCentavos ?: return 0
    if (base <= 0 || preco.plano == Plano.MENSAL) return 0
    val desconto = (base - preco.porMesCentavos) * 100 / base
    return desconto.toInt().coerceIn(0, 99)
}

private fun Context.abrirNoNavegador(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Sem navegador instalado é raríssimo; o botão "Já paguei" ainda salva.
    }
}
