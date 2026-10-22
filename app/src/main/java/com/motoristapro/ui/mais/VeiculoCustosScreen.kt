package com.motoristapro.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.custo.CalculadoraCusto
import com.motoristapro.custo.DadosCusto
import com.motoristapro.custo.FatiasCusto
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.repository.custoFixoDiario
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.CartaoDestaque
import com.motoristapro.ui.componentes.CorSobreDestaque
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.componentes.SeloIcone
import com.motoristapro.ui.componentes.TextoAjuda
import com.motoristapro.ui.custo.BarraFatias
import com.motoristapro.ui.custo.CorCombustivel
import com.motoristapro.ui.custo.CorFixos
import com.motoristapro.ui.custo.CorManutencao
import com.motoristapro.ui.custo.QuadroMeta
import com.motoristapro.ui.custo.centavosPorKm
import com.motoristapro.ui.paraCentavos
import com.motoristapro.ui.theme.AzulInfo
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Roxo
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import java.util.Locale

private val PT = Locale("pt", "BR")

// ====================================================== meu veículo e custos (4.1)

/** O custo por km grande, com a barra de onde ele vem (combustível, manutenção, fixos). */
@Composable
internal fun CustoPorKmCard(cfg: Configuracao, dados: DadosCusto?, onAbrir: () -> Unit) {
    val fatias = dados?.let { FatiasCusto.de(CalculadoraCusto.calcular(it)) } ?: FatiasCusto.ZERO
    CartaoDestaque {
        Rotulo("Seu custo por km")
        if (cfg.custoKmCentavos > 0) {
            Text(
                buildAnnotatedString {
                    append(cfg.custoKmCentavos.emReais())
                    withStyle(SpanStyle(fontSize = 20.sp, color = TextoSecundario)) { append(" /km") }
                },
                style = EstiloValor, fontSize = 42.sp
            )
            TextoAjuda("É o que o app desconta de cada corrida para mostrar o lucro.")
            if (fatias.total > 0) {
                BarraFatias(fatias, Modifier.fillMaxWidth().height(12.dp).padding(top = 4.dp))
                Row(Modifier.fillMaxWidth()) {
                    LegendaFatia("Combustível", fatias.combustivel, CorCombustivel, Modifier.weight(1f))
                    LegendaFatia("Manutenção", fatias.manutencao, CorManutencao, Modifier.weight(1f))
                    LegendaFatia("Fixos", fatias.fixos, CorFixos, Modifier.weight(1f))
                }
            }
        } else {
            TextoAjuda(
                "Você ainda não calculou seu custo. Sem ele, o app mostra o valor bruto da " +
                    "corrida — não o que sobra pra você."
            )
        }
        Button(
            onClick = onAbrir,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
        ) {
            Text(if (cfg.custoKmCentavos > 0) "Recalcular meu custo" else "Calcular meu custo", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LegendaFatia(nome: String, porKm: Double, cor: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
            Text(nome, fontSize = 11.sp, color = TextoSecundario, modifier = Modifier.padding(start = 4.dp))
        }
        Text(centavosPorKm(porKm).removePrefix("R$ "), style = EstiloValor, fontSize = 15.sp)
    }
}

/** O veículo num cartão só: ícone, nome e etiquetas (moto, gasolina, 35 km/L, quitada). */
@Composable
internal fun VeiculoCard(cfg: Configuracao, dados: DadosCusto?, onAbrir: () -> Unit) {
    val nome = cfg.veiculoNome?.takeIf { it.isNotBlank() } ?: dados?.veiculoNome?.takeIf { it.isNotBlank() }
    val tipo = dados?.tipoVeiculo ?: TipoVeiculo.MOTO
    val moto = tipo != TipoVeiculo.CARRO
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie)
            .clickable { onAbrir() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeloIcone(Icones.doVeiculo(tipo), AzulInfo, tamanho = 46.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(nome ?: "Veículo não informado", fontWeight = FontWeight.Bold)
            val etiquetas = buildList {
                dados?.let { add(it.tipoVeiculo.rotulo) }
                dados?.let { add(it.combustivel.rotulo) }
                if (cfg.consumoKmLx100 > 0) {
                    add(String.format(PT, "%.0f %s", cfg.consumoKmLx100 / 100.0, dados?.combustivel?.unidade ?: "km/L"))
                }
                dados?.let { add(if (moto) it.forma.rotulo.replace("do", "da") else it.forma.rotulo) }
            }
            if (etiquetas.isEmpty()) {
                TextoAjuda("Toque para preencher no assistente")
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    etiquetas.forEach { t ->
                        Text(
                            t, fontSize = 12.sp, color = TextoSecundario, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(SuperficieAlta)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = TextoSecundario)
    }
}

/** Metas em três quadradinhos (dia, semana, mês). Todas saem da mensal, no assistente. */
@Composable
internal fun MetasCard(cfg: Configuracao, onEditar: () -> Unit) {
    Cartao(espaco = 10) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo("Suas metas de lucro", Modifier.weight(1f))
            Text(
                "Editar", color = Lima, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEditar() }.padding(4.dp)
            )
        }
        if (cfg.metaLucroMensalCentavos <= 0) {
            TextoAjuda(
                "Sem meta cadastrada. O assistente pergunta quanto você quer levar por mês " +
                    "e divide pelos dias trabalhados."
            )
            return@Cartao
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuadroMeta("Por dia", cfg.metaLucroDiarioCentavos, Lima, Modifier.weight(1f))
            QuadroMeta("Semana", cfg.metaLucroSemanalCentavos, TextoPrincipal, Modifier.weight(1f))
            QuadroMeta("Mês", cfg.metaLucroMensalCentavos, TextoPrincipal, Modifier.weight(1f))
        }
        TextoAjuda("${cfg.diasTrabalhoMes} dias por mês · a meta do dia é a barra do Início")
    }
}

/**
 * Os custos fixos do mês numa lista com ícone. Os que vêm do assistente não têm
 * lixeira: apagar não adiantaria, eles voltam no próximo cálculo.
 */
@Composable
internal fun CustosFixosCard(
    custos: List<CustoFixo>,
    diasTrabalhoMes: Int,
    onExcluir: (CustoFixo) -> Unit,
    onAdicionar: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie).padding(horizontal = 14.dp)) {
        Rotulo("Custos fixos do mês", Modifier.padding(top = 14.dp, bottom = 4.dp))
        custos.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SeloIcone(if (c.doAssistente) Icones.Escudo else Icones.Alfinete, if (c.doAssistente) Turquesa else Roxo)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(c.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (c.doAssistente) "do assistente de custo" else "adicionado por você",
                        style = MaterialTheme.typography.labelSmall, color = TextoSecundario
                    )
                }
                Text(
                    buildAnnotatedString {
                        append(c.valorMensalCentavos.emReais().removePrefix("R$ "))
                        withStyle(SpanStyle(fontSize = 11.sp, color = TextoSecundario)) { append("/mês") }
                    },
                    style = EstiloValor, fontSize = 15.sp
                )
                if (!c.doAssistente) {
                    IconButton(onClick = { onExcluir(c) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = Contorno)
        }
        Row(
            Modifier.fillMaxWidth().clickable { onAdicionar() }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeloIcone(Icones.Mais, Roxo)
            Text(
                "Adicionar custo fixo", color = Lima, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        val total = custos.filter { it.ativo }.sumOf { it.valorMensalCentavos }
        if (total > 0) {
            TextoAjuda(
                "Total ${total.emReais()}/mês · ${custoFixoDiario(total, diasTrabalhoMes).emReais()} por dia trabalhado",
                Modifier.padding(bottom = 14.dp)
            )
        }
    }
}

@Composable
internal fun NovoCustoFixoDialog(onDismiss: () -> Unit, onSalvar: (String, Long) -> Unit) {
    var nome by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    val valorC = valor.paraCentavos()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo custo fixo mensal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoFormulario("Nome (ex.: Seguro, Parcela)", nome, { nome = it }, numerico = false)
                CampoFormulario("Valor por mês (R$)", valor, { valor = it }, erro = valor.isNotBlank() && valorC == null)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valorC != null && nome.isNotBlank()) onSalvar(nome, valorC) },
                enabled = valorC != null && nome.isNotBlank()
            ) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
