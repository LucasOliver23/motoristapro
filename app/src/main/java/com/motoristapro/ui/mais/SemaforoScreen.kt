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
import com.motoristapro.data.repository.emReais
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
