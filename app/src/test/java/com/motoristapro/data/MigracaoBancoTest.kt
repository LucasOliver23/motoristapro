package com.motoristapro.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.motoristapro.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Confere a migração v13 -> v14 (mais de um veículo) num banco de verdade.
 *
 * Os JSON de schema antigos não estão no repositório, então o banco v13 é
 * montado a partir do v14: tira a tabela `veiculos` e a coluna `veiculo_id`
 * e volta o `user_version` para 13. Ao reabrir, o Room roda a migração e
 * compara tabela por tabela com as entidades — se a migração esquecer uma
 * coluna, um DEFAULT ou uma chave, ele lança erro e o teste cai aqui, não no
 * celular do motorista.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigracaoBancoTest {

    private val nome = "migracao-teste.db"
    private lateinit var context: Context

    @Before
    fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(nome)
    }

    @After
    fun limpar() {
        context.deleteDatabase(nome)
    }

    @Test
    fun migra13para14SemPerderDados() {
        // 1) Banco atual (v14), criado pelo próprio Room.
        Room.databaseBuilder(context, AppDatabase::class.java, nome)
            .allowMainThreadQueries().build().apply {
                openHelper.writableDatabase
                close()
            }

        // 2) Volta para a forma da v13, com uma corrida e uma despesa dentro.
        val bruto = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(nome).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        bruto.execSQL("DROP TABLE veiculos")
        listOf("corridas", "despesas", "jornadas", "manutencoes").forEach { tirarColunaVeiculo(bruto, it) }
        bruto.execSQL("INSERT INTO plataformas (nome, cor_hex, ativa) VALUES ('Teste', '#000000', 1)")
        val plataforma = bruto.rawQuery("SELECT id FROM plataformas WHERE nome = 'Teste'", null)
            .use { it.moveToFirst(); it.getLong(0) }
        inserirMinimo(bruto, "corridas", mapOf("plataforma_id" to plataforma))
        inserirMinimo(bruto, "despesas", emptyMap())
        bruto.version = 13
        bruto.close()

        // 3) Reabre com a migração: o Room valida o schema inteiro aqui.
        val banco = Room.databaseBuilder(context, AppDatabase::class.java, nome)
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries().build()
        val db = banco.openHelper.writableDatabase

        assertEquals(1, contar(db, "SELECT COUNT(*) FROM veiculos WHERE id = 1"))
        assertEquals(1, contar(db, "SELECT COUNT(*) FROM corridas WHERE veiculo_id = 1"))
        assertEquals(1, contar(db, "SELECT COUNT(*) FROM despesas WHERE veiculo_id = 1"))
        banco.close()
    }

    /** Recria a tabela sem a coluna `veiculo_id`, mantendo chaves e índices (como era na v13). */
    private fun tirarColunaVeiculo(db: android.database.sqlite.SQLiteDatabase, tabela: String) {
        val criar = db.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tabela))
            .use { it.moveToFirst(); it.getString(0) }
        val indices = db.rawQuery(
            "SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL", arrayOf(tabela)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val semColuna = criar.replace(Regex(""",\s*`veiculo_id` INTEGER NOT NULL DEFAULT 1"""), "")
        check(semColuna != criar) { "coluna veiculo_id não encontrada em $tabela: $criar" }
        db.execSQL("DROP TABLE `$tabela`")
        db.execSQL(semColuna)
        indices.forEach { db.execSQL(it) }
    }

    /** Insere uma linha com o mínimo: zero em toda coluna NOT NULL sem DEFAULT. */
    private fun inserirMinimo(db: android.database.sqlite.SQLiteDatabase, tabela: String, fixos: Map<String, Any>) {
        val valores = android.content.ContentValues()
        db.rawQuery("PRAGMA table_info(`$tabela`)", null).use { c ->
            while (c.moveToNext()) {
                val coluna = c.getString(c.getColumnIndexOrThrow("name"))
                val tipo = c.getString(c.getColumnIndexOrThrow("type"))
                val obrigatoria = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1
                val temPadrao = !c.isNull(c.getColumnIndexOrThrow("dflt_value"))
                val chave = c.getInt(c.getColumnIndexOrThrow("pk")) > 0
                if (chave || !obrigatoria || temPadrao || coluna in fixos) continue
                if (tipo.equals("TEXT", true)) valores.put(coluna, "OUTROS") else valores.put(coluna, 0L)
            }
        }
        fixos.forEach { (k, v) -> valores.put(k, v as Long) }
        db.insertOrThrow(tabela, null, valores)
    }

    private fun contar(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { it.moveToFirst(); it.getInt(0) }
}
