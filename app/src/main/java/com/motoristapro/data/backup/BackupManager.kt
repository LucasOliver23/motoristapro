package com.motoristapro.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Backup, restauração e exportação CSV.
 * Usa o seletor de arquivos do Android (Storage Access Framework): o motorista escolhe
 * onde salvar — Downloads, Google Drive, WhatsApp... — sem pedir permissão de armazenamento.
 */
class BackupManager(private val context: Context) {

    private val app: MotoristaApp get() = context.applicationContext as MotoristaApp
    private val pt = Locale("pt", "BR")
    private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", pt)

    // ================================================================== backup completo

    /** Copia o banco inteiro (corridas, despesas, ofertas, configurações...) para [destino]. */
    suspend fun fazerBackup(destino: Uri) = withContext(Dispatchers.IO) {
        val db = app.database
        // Grava o conteúdo pendente do WAL no arquivo principal antes de copiar.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val arquivo = context.getDatabasePath(AppDatabase.NOME_BANCO)
        val saida = context.contentResolver.openOutputStream(destino)
            ?: error("Não foi possível abrir o arquivo de destino")
        saida.use { s -> arquivo.inputStream().use { it.copyTo(s) } }
        Unit
    }

    /**
     * Restaura um backup. Valida o arquivo antes de tocar no banco atual.
     * Depois de retornar com sucesso, chame [reiniciarApp].
     */
    suspend fun restaurar(origem: Uri) = withContext(Dispatchers.IO) {
        val arquivoBanco = context.getDatabasePath(AppDatabase.NOME_BANCO)
        val temp = File(arquivoBanco.parentFile, "restauracao_tmp.db")
        try {
            context.contentResolver.openInputStream(origem)?.use { entrada ->
                temp.outputStream().use { entrada.copyTo(it) }
            } ?: error("Não foi possível ler o arquivo escolhido")

            validarBackup(temp)

            // Troca atômica: fecha o Room, renomeia o temporário e apaga os arquivos do WAL antigo.
            AppDatabase.fechar()
            File(arquivoBanco.path + "-wal").delete()
            File(arquivoBanco.path + "-shm").delete()
            if (!temp.renameTo(arquivoBanco)) {
                temp.copyTo(arquivoBanco, overwrite = true)
            }
            Unit
        } finally {
            temp.delete()
        }
    }

    private fun validarBackup(arquivo: File) {
        val cabecalho = ByteArray(100)
        arquivo.inputStream().use { if (it.read(cabecalho) < 100) error("Arquivo inválido ou vazio") }
        val assinatura = String(cabecalho, 0, 15, StandardCharsets.US_ASCII)
        if (assinatura != "SQLite format 3") error("Este arquivo não é um backup do MotoristaPro")

        // user_version do SQLite (bytes 60..63, big-endian) = versão do esquema do Room.
        val versao = ((cabecalho[60].toInt() and 0xFF) shl 24) or ((cabecalho[61].toInt() and 0xFF) shl 16) or
            ((cabecalho[62].toInt() and 0xFF) shl 8) or (cabecalho[63].toInt() and 0xFF)
        if (versao < 1) error("Backup sem versão reconhecida")
        if (versao > AppDatabase.VERSAO) error("Backup feito numa versão mais nova do app. Atualize o app primeiro.")

        SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('corridas','despesas')", null)
                .use { if (it.count < 2) error("Este arquivo não é um backup do MotoristaPro") }
        }
    }

    /** Reabre o app do zero (necessário depois de restaurar o banco). */
    fun reiniciarApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    // ================================================================== exportação CSV (Excel)

    suspend fun exportarCorridas(destino: Uri): Int = withContext(Dispatchers.IO) {
        val lista = app.database.corridaDao().listarTodasComPlataforma()
        escreverCsv(
            destino,
            listOf("Data", "Plataforma", "Origem", "Destino", "Valor", "Gorjeta", "Total", "Km", "Minutos", "R$/km", "Observação"),
            lista.map { item ->
                val c = item.corrida
                val km = (c.distanciaMetros + c.deslocamentoMetros) / 1000.0
                listOf(
                    data(c.inicioEm), item.plataformaNome, c.origem, c.destino,
                    reais(c.valorCentavos), reais(c.gorjetaCentavos), reais(c.receitaCentavos),
                    decimal(km, 2), (c.duracaoSegundos / 60).toString(),
                    if (km > 0) decimal(c.receitaCentavos / 100.0 / km, 2) else "",
                    c.observacao ?: ""
                )
            }
        )
        lista.size
    }

    suspend fun exportarDespesas(destino: Uri): Int = withContext(Dispatchers.IO) {
        val lista = app.database.despesaDao().listarTodas()
        escreverCsv(
            destino,
            listOf("Data", "Categoria", "Valor", "Litros", "Hodômetro", "Descrição"),
            lista.map { d ->
                listOf(
                    data(d.dataEm), d.categoria.rotulo, reais(d.valorCentavos),
                    d.litrosMl?.let { decimal(it / 1000.0, 2) } ?: "",
                    d.odometroKm?.toString() ?: "", d.descricao ?: ""
                )
            }
        )
        lista.size
    }

    suspend fun exportarOfertas(destino: Uri): Int = withContext(Dispatchers.IO) {
        val lista = app.database.ofertaDao().listarTodas()
        escreverCsv(
            destino,
            listOf("Data", "Plataforma", "Valor", "Km", "Minutos", "Entregas", "R$/km", "Classificação", "Registrada"),
            lista.map { o ->
                listOf(
                    data(o.recebidaEm), o.plataforma, reais(o.valorCentavos), decimal(o.km, 2),
                    o.minutos.toString(), o.paradas.toString(), reais(o.reaisPorKmCentavos),
                    o.classificacao, if (o.registrada) "Sim" else "Não"
                )
            }
        )
        lista.size
    }

    /** CSV com ";" e BOM UTF-8: abre direto no Excel em português com acentos e vírgula decimal. */
    private fun escreverCsv(destino: Uri, cabecalho: List<String>, linhas: List<List<String>>) {
        val saida = context.contentResolver.openOutputStream(destino) ?: error("Não foi possível abrir o arquivo")
        OutputStreamWriter(saida, StandardCharsets.UTF_8).use { w ->
            w.write("﻿")
            w.write(cabecalho.joinToString(";") { campo(it) })
            w.write("\r\n")
            linhas.forEach { linha ->
                w.write(linha.joinToString(";") { campo(it) })
                w.write("\r\n")
            }
        }
    }

    private fun campo(v: String): String =
        if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun data(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(formatoData)
    private fun reais(centavos: Long): String = String.format(pt, "%.2f", centavos / 100.0)
    private fun decimal(v: Double, casas: Int): String = String.format(pt, "%.${casas}f", v)
}
