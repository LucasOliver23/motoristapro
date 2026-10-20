package com.motoristapro.relatorio

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.repository.Periodo
import com.motoristapro.data.repository.emReais
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Os números do mês que vão no PDF. Separados do desenho para a tela mostrar a prévia. */
data class ResumoMes(
    val mes: YearMonth,
    val motorista: String?,
    val cidade: String?,
    val veiculo: String?,
    val corridas: Int,
    val ganhosCentavos: Long,
    /** "Uber" -> centavos, do maior para o menor. */
    val porApp: List<Pair<String, Long>>,
    val combustivelCentavos: Long,
    val outrasDespesasCentavos: Long,
    val custosFixosCentavos: Long,
    val metros: Long,
    val segundosJornada: Long,
    val linhasCorridas: List<String>,
    val linhasDespesas: List<String>
) {
    val lucroCentavos: Long get() = ganhosCentavos - combustivelCentavos - outrasDespesasCentavos - custosFixosCentavos
    val porKmCentavos: Long get() = if (metros > 0) ganhosCentavos * 1000 / metros else 0
    val porHoraCentavos: Long get() = if (segundosJornada > 0) ganhosCentavos * 3600 / segundosJornada else 0
}

/**
 * Relatório do mês em PDF (Finanças ▸ Relatório do mês): ganhos por app,
 * despesas, custos fixos e lucro, com a lista do mês no fim. Serve para
 * imposto, banco e financiamento. Feito com o PdfDocument do Android.
 */
object RelatorioPdf {

    private val PT = Locale("pt", "BR")
    private val DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    suspend fun montar(context: Context, mes: YearMonth): ResumoMes = withContext(Dispatchers.IO) {
        val repo = (context.applicationContext as MotoristaApp).repository
        val (ini, fim) = Periodo.mes(mes)
        val zona = ZoneId.systemDefault()
        val cfg = repo.configuracao().first()
        val corridas = repo.corridasDoPeriodo(ini, fim).first().sortedBy { it.corrida.inicioEm }
        val despesas = repo.despesasDoPeriodo(ini, fim).first().sortedBy { it.dataEm }
        val fixos = repo.custoFixoMensal().first()
        val jornada = runCatching { repo.totaisJornadasEncerradas(ini, fim).first().segundos }.getOrDefault(0L)

        fun quando(ms: Long) = Instant.ofEpochMilli(ms).atZone(zona).format(DIA_HORA)
        ResumoMes(
            mes = mes,
            motorista = cfg.nomeMotorista,
            cidade = cfg.cidade,
            veiculo = cfg.veiculoNome,
            corridas = corridas.size,
            ganhosCentavos = corridas.sumOf { it.corrida.valorCentavos + it.corrida.gorjetaCentavos },
            porApp = corridas.groupBy { it.plataformaNome }
                .map { (app, l) -> app to l.sumOf { it.corrida.valorCentavos + it.corrida.gorjetaCentavos } }
                .sortedByDescending { it.second },
            combustivelCentavos = despesas.filter { it.categoria == CategoriaDespesa.COMBUSTIVEL }.sumOf { it.valorCentavos },
            outrasDespesasCentavos = despesas.filter { it.categoria != CategoriaDespesa.COMBUSTIVEL }.sumOf { it.valorCentavos },
            custosFixosCentavos = fixos,
            metros = corridas.sumOf { it.corrida.distanciaMetros + it.corrida.deslocamentoMetros },
            segundosJornada = jornada,
            linhasCorridas = corridas.map {
                val c = it.corrida
                "${quando(c.inicioEm)}  ${it.plataformaNome}  ${String.format(PT, "%.1f km", c.distanciaMetros / 1000.0)}  " +
                    (c.valorCentavos + c.gorjetaCentavos).emReais()
            },
            linhasDespesas = despesas.map {
                "${quando(it.dataEm)}  ${it.categoria.rotulo}${it.descricao?.let { d -> " · $d" } ?: ""}  ${it.valorCentavos.emReais()}"
            }
        )
    }

    /** Escreve o PDF na pasta de relatórios e devolve o arquivo. */
    suspend fun gerar(context: Context, r: ResumoMes): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val largura = 595
        val altura = 842
        val margem = 40f
        var numero = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(largura, altura, numero).create())
        var y = margem

        val titulo = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val texto = Paint().apply { textSize = 11f; color = Color.BLACK; isAntiAlias = true }
        val cinza = Paint(texto).apply { color = Color.rgb(110, 110, 110) }
        val negrito = Paint(texto).apply { typeface = Typeface.DEFAULT_BOLD }
        val verde = Paint(negrito).apply { color = Color.rgb(10, 125, 90); textSize = 13f }
        val linha = Paint().apply { color = Color.rgb(225, 225, 225); strokeWidth = 1f }

        fun novaPaginaSePrecisar(espaco: Float) {
            if (y + espaco <= altura - margem) return
            doc.finishPage(pagina)
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(largura, altura, numero).create())
            y = margem
        }

        fun linhaValor(rotulo: String, valor: String, p: Paint = negrito) {
            novaPaginaSePrecisar(20f)
            y += 16f
            pagina.canvas.drawText(rotulo, margem, y, texto)
            pagina.canvas.drawText(valor, largura - margem - p.measureText(valor), y, p)
            y += 5f
            pagina.canvas.drawLine(margem, y, largura - margem, y, linha)
        }

        val nomeMes = r.mes.month.getDisplayName(TextStyle.FULL, PT)
        y += 18f
        pagina.canvas.drawText("MotoristaPro · Relatório de $nomeMes/${r.mes.year}", margem, y, titulo)
        y += 16f
        pagina.canvas.drawText(listOfNotNull(r.motorista, r.cidade, r.veiculo).joinToString(" · "), margem, y, cinza)
        y += 10f

        linhaValor("Ganhos brutos (${r.corridas} corridas)", r.ganhosCentavos.emReais())
        r.porApp.forEach { (app, v) -> linhaValor("   $app", v.emReais(), texto) }
        linhaValor("Combustível", "− " + r.combustivelCentavos.emReais())
        linhaValor("Manutenção e outras despesas", "− " + r.outrasDespesasCentavos.emReais())
        linhaValor("Custos fixos do mês", "− " + r.custosFixosCentavos.emReais())
        linhaValor("Lucro líquido", r.lucroCentavos.emReais(), verde)
        y += 16f
        pagina.canvas.drawText(
            buildList {
                add(String.format(PT, "%,.0f km", r.metros / 1000.0))
                if (r.segundosJornada > 0) add("${r.segundosJornada / 3600} h de jornada")
                if (r.porKmCentavos > 0) add("${r.porKmCentavos.emReais()}/km")
                if (r.porHoraCentavos > 0) add("${r.porHoraCentavos.emReais()}/h")
            }.joinToString(" · "),
            margem, y, cinza
        )

        fun lista(tituloLista: String, itens: List<String>) {
            novaPaginaSePrecisar(40f)
            y += 30f
            pagina.canvas.drawText(tituloLista, margem, y, negrito)
            y += 4f
            if (itens.isEmpty()) {
                y += 14f
                pagina.canvas.drawText("Nenhum lançamento no mês.", margem, y, cinza)
            }
            itens.forEach { t ->
                novaPaginaSePrecisar(16f)
                y += 14f
                pagina.canvas.drawText(t, margem, y, texto)
            }
        }
        lista("Corridas", r.linhasCorridas)
        lista("Despesas", r.linhasDespesas)

        novaPaginaSePrecisar(30f)
        y += 28f
        pagina.canvas.drawText(
            "Gerado pelo MotoristaPro em " + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            margem, y, cinza
        )
        doc.finishPage(pagina)

        val pasta = File(context.cacheDir, "relatorios").apply { mkdirs() }
        val arquivo = File(pasta, "motoristapro-${r.mes}.pdf")
        arquivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        arquivo
    }

    /** Abre o "Compartilhar" do Android (WhatsApp, e-mail, Drive...). */
    fun compartilhar(context: Context, arquivo: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", arquivo)
        val envio = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Relatório MotoristaPro")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(envio, "Enviar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
