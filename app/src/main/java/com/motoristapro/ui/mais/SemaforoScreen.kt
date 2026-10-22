package com.motoristapro.ui.mais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.custo.MetaPorHora
import com.motoristapro.data.repository.emReais
import com.motoristapro.service.ParametrosRegua
import com.motoristapro.service.ReguaLucroHora
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.FaixaTresCores
import com.motoristapro.ui.componentes.LinhaFaixa
import com.motoristapro.ui.componentes.LinhaSwitch
import com.motoristapro.ui.componentes.TabelaFaixas
import com.motoristapro.ui.componentes.TextoAjuda
import com.motoristapro.ui.paraCentavosOuZero
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import java.util.Locale

private val PT = Locale("pt", "BR")

/**
 * Como o cartão decide (4.16): a régua do lucro por hora, com o que já é do
 * motorista (medido) e o que ainda é padrão (estimado), e a chave das faixas
 * avançadas. Sem meta ou custo, explica o que falta para a régua valer.
 */
@Composable
internal fun CardComoDecide(
    regua: ParametrosRegua?,
    avancado: Boolean,
    onAvancado: (Boolean) -> Unit,
    onAbrirAssistente: () -> Unit
) {
    CardSecao(titulo = "Como o cartão decide") {
        if (regua == null) {
            TextoAjuda(
                "Hoje a cor vem das faixas abaixo. Para o cartão decidir pelo LUCRO POR HORA, " +
                    "faça o assistente de custo (custo por km e meta de lucro) e escolha as horas por dia em Suas metas."
            )
            TextButton(onClick = onAbrirAssistente) { Text("Abrir o assistente de custo") }
            return@CardSecao
        }
        val fracao = regua.fracaoMedida ?: MetaPorHora.FRACAO_PADRAO
        val alvo = regua.metaLimpaHoraCentavos / 100.0 / fracao
        TextoAjuda("A corrida compensa quando o lucro dela por hora bate a sua meta, já contando a espera entre corridas.")
        LinhaValor("Sua meta limpa", String.format(PT, "R$ %.2f por hora de turno", regua.metaLimpaHoraCentavos / 100.0))
        LinhaValor(
            "Tempo com corrida",
            String.format(PT, "%.0f%% do turno", fracao * 100) + if (regua.fracaoMedida == null) " (estimado)" else " (medido)",
            cor = if (regua.fracaoMedida == null) AmareloAlerta else MaterialTheme.colorScheme.onSurface
        )
        LinhaValor("A corrida precisa render", String.format(PT, "R$ %.2f por hora de corrida", alvo), negrito = true)
        LinhaValor(
            "Seu custo",
            String.format(PT, "R$ %.2f/km", regua.custoKmCentavos / 100.0) +
                if (regua.custoBasico) " (básico)" else " (completo)",
            cor = if (regua.custoBasico) AmareloAlerta else MaterialTheme.colorScheme.onSurface
        )
        if (regua.custoBasico) {
            TextoAjuda("Custo básico: ainda sem óleo, pneus e revisão. Complete no assistente de custo para ficar exato.")
        }
        LinhaValor(
            "Velocidade nas corridas",
            String.format(PT, "%.0f km/h", regua.velocidadeMedidaKmH ?: ReguaLucroHora.VELOCIDADE_PADRAO_KMH) +
                if (regua.velocidadeMedidaKmH == null) " (estimado)" else " (medido)",
            cor = if (regua.velocidadeMedidaKmH == null) AmareloAlerta else MaterialTheme.colorScheme.onSurface
        )
        TextoAjuda(
            String.format(
                PT,
                "ACEITAR: lucro de R$ %.0f/h ou mais. ATENÇÃO: de R$ %.0f a R$ %.0f/h. RECUSAR: abaixo disso ou com prejuízo. " +
                    "A velocidade só entra quando o card não mostra o tempo (entregas). \"Estimado\" vira o seu número " +
                    "depois de 3 horas de turno e ${ReguaLucroHora.MINIMO_CORRIDAS_VELOCIDADE} corridas registradas.",
                alvo, alvo * (1 - MetaPorHora.MARGEM_AMARELO), alvo
            )
        )
        LinhaSwitch(
            "Faixas avançadas",
            "Usa também as faixas de R$/km, R$/hora, nota e valor mínimo abaixo como trava a mais: " +
                "a corrida fica com a pior das duas cores.",
            avancado, onAvancado
        )
    }
}

private fun Long.campo(): String = emReais().removePrefix("R$ ")

/**
 * As faixas do semáforo. Tudo aqui salva na hora — arrastou, valeu na próxima oferta.
 *
 * Lucro mínimo em R$ e em % ficam BLOQUEADOS enquanto não existir custo por km:
 * sem o custo não há lucro a calcular, e deixar o motorista configurar algo que
 * nunca vai valer é pior do que não oferecer.
 */
@Composable
internal fun CardSuasFaixas(
    faixas: EstadoFaixas,
    temCusto: Boolean,
    valorMinimoViagem: Long,
    onFaixaKm: (Float, Float) -> Unit,
    onFaixaHora: (Float, Float) -> Unit,
    onFaixaNota: (Float, Float) -> Unit,
    onUsarNota: (Boolean) -> Unit,
    onLucroMinimo: (Float) -> Unit,
    onLucroPercent: (Float) -> Unit,
    onValorMinimo: (Long) -> Unit,
    onAbrirAssistente: () -> Unit,
    /** O R$/hora que bate a meta (Menu ▸ Suas metas); vira um risco na régua. */
    metaHora: Int? = null
) {
    CardSecao(titulo = "Suas faixas") {
        TabelaFaixas(
            listOf(
                LinhaFaixa(
                    "R$/km",
                    String.format(PT, "< %.2f", faixas.kmRuim),
                    String.format(PT, "%.2f–%.2f", faixas.kmRuim, faixas.kmBoa),
                    String.format(PT, "≥ %.2f", faixas.kmBoa)
                ),
                LinhaFaixa(
                    "R$/hora",
                    String.format(PT, "< %.0f", faixas.horaRuim),
                    String.format(PT, "%.0f–%.0f", faixas.horaRuim, faixas.horaBoa),
                    String.format(PT, "≥ %.0f", faixas.horaBoa)
                )
            ) + if (faixas.usarNota) listOf(
                LinhaFaixa(
                    "Nota",
                    String.format(PT, "< %.2f", faixas.notaRuim),
                    String.format(PT, "%.2f–%.2f", faixas.notaRuim, faixas.notaBoa),
                    String.format(PT, "≥ %.2f", faixas.notaBoa)
                )
            ) else emptyList()
        )

        HorizontalDivider(color = Contorno, modifier = Modifier.padding(vertical = 4.dp))

        FaixaTresCores(
            titulo = "Ganho por km",
            unidade = "R$/km",
            inicio = faixas.kmRuim, fim = faixas.kmBoa,
            // Régua até R$ 4/km: até 6 os valores de verdade (0,50 a 2,50) ficavam
            // espremidos no primeiro terço e cada milímetro do dedo pulava 10 centavos.
            minimo = 0.3f, maximo = 4f, degrau = 0.05f,
            formatar = { String.format(PT, "R$ %.2f", it) },
            onMudar = onFaixaKm
        )

        FaixaTresCores(
            titulo = "Ganho por hora",
            unidade = "R$/hora",
            inicio = faixas.horaRuim, fim = faixas.horaBoa,
            // Meta alta (poucas horas, custo alto) passa de R$ 80: a régua cresce
            // junto para o verde dela caber.
            minimo = 5f, maximo = maxOf(80f, faixas.horaBoa + 10f, (metaHora ?: 0) + 10f), degrau = 1f,
            formatar = { String.format(PT, "R$ %.0f", it) },
            onMudar = onFaixaHora,
            marca = metaHora?.toFloat(),
            ajuda = metaHora?.let { "Risco branco: sua meta, R$ $it/h (Menu ▸ Suas metas)." }
        )

        LinhaSwitch(
            "Usar a nota do passageiro",
            "Só vale nas telas que mostram a nota. Uber mostra; a 99 nem sempre.",
            faixas.usarNota, onUsarNota
        )
        if (faixas.usarNota) {
            FaixaTresCores(
                titulo = "Nota do passageiro",
                unidade = "estrelas",
                inicio = faixas.notaRuim, fim = faixas.notaBoa,
                minimo = 3f, maximo = 5f, degrau = 0.05f,
                formatar = { String.format(PT, "★ %.2f", it) },
                onMudar = onFaixaNota
            )
        }

        HorizontalDivider(color = Contorno, modifier = Modifier.padding(vertical = 4.dp))
        Text("Condições mínimas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        TextoAjuda("Violou qualquer uma, a oferta fica vermelha na hora — não importa o resto.")

        CampoMinimo(
            rotulo = "Valor mínimo da viagem (R$)",
            valorInicial = if (valorMinimoViagem > 0) valorMinimoViagem.campo() else "",
            habilitado = true,
            onValor = { onValorMinimo(it.paraCentavosOuZero() ?: 0L) }
        )
        CampoMinimo(
            rotulo = "Lucro líquido mínimo (R$)",
            valorInicial = if (faixas.lucroMinimo > 0f) String.format(PT, "%.2f", faixas.lucroMinimo) else "",
            habilitado = temCusto,
            onValor = { onLucroMinimo(((it.paraCentavosOuZero() ?: 0L) / 100f)) }
        )
        CampoMinimo(
            rotulo = "Porcentagem de lucro (%)",
            valorInicial = if (faixas.lucroPercent > 0f) faixas.lucroPercent.toInt().toString() else "",
            habilitado = temCusto,
            onValor = { onLucroPercent(it.filter(Char::isDigit).toFloatOrNull() ?: 0f) }
        )

        if (!temCusto) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Requer custo por km",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmareloAlerta,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onAbrirAssistente) { Text("Calcular agora", color = Lima) }
            }
        }
    }
}

/** Campo de mínimo: quando bloqueado, mostra "Requer custo" em vez de aceitar digitação. */
@Composable
private fun CampoMinimo(
    rotulo: String,
    valorInicial: String,
    habilitado: Boolean,
    onValor: (String) -> Unit
) {
    var texto by rememberSaveable(rotulo) { mutableStateOf(valorInicial) }
    if (!habilitado) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            Text("Requer custo", style = MaterialTheme.typography.bodySmall, color = AmareloAlerta)
        }
        return
    }
    CampoFormulario(
        rotulo = rotulo,
        valor = texto,
        onValor = {
            texto = it
            onValor(it)
        }
    )
}
