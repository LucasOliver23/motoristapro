package com.motoristapro.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.motoristapro.data.local.converter.Converters
import com.motoristapro.data.local.dao.ConfiguracaoDao
import com.motoristapro.data.local.dao.CustoFixoDao
import com.motoristapro.data.local.dao.CorridaDao
import com.motoristapro.data.local.dao.DespesaDao
import com.motoristapro.data.local.dao.JornadaDao
import com.motoristapro.data.local.dao.OfertaDao
import com.motoristapro.data.local.dao.PlataformaDao
import com.motoristapro.data.local.dao.SincronizacaoDao
import com.motoristapro.data.local.dao.RelatorioDao
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.data.local.entity.Plataforma

/** Versão atual do esquema do banco (constante de topo: pode ser usada na anotação). */
const val VERSAO_BANCO = 6

/**
 * Banco local do MotoristaPro.
 *
 * Histórico de versões:
 *  1 -> esquema inicial (plataformas, corridas, despesas, configuracoes)
 *  2 -> configuracoes.tarifa_minima_centavos
 *  3 -> meta semanal + veículo (consumo, preço do litro) + tabela jornadas
 *  4 -> plataforma iFood (só dados)
 *  5 -> tabelas ofertas (histórico) e custos_fixos
 *  6 -> perfil do motorista (nome, telefone, cidade)
 *
 * Ao alterar qualquer @Entity: suba [version], escreva a Migration e registre em [get].
 * O JSON de cada versão é exportado para app/schemas/ (versione no git).
 */
@Database(
    entities = [
        Plataforma::class,
        Corrida::class,
        Despesa::class,
        Configuracao::class,
        Jornada::class,
        OfertaRecebida::class,
        CustoFixo::class
    ],
    version = VERSAO_BANCO,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun plataformaDao(): PlataformaDao
    abstract fun corridaDao(): CorridaDao
    abstract fun despesaDao(): DespesaDao
    abstract fun configuracaoDao(): ConfiguracaoDao
    abstract fun relatorioDao(): RelatorioDao
    abstract fun jornadaDao(): JornadaDao
    abstract fun ofertaDao(): OfertaDao
    abstract fun custoFixoDao(): CustoFixoDao
    abstract fun sincronizacaoDao(): SincronizacaoDao

    companion object {
        /** Versão atual do esquema (usada também para validar backups). */
        const val VERSAO = VERSAO_BANCO
        const val NOME_BANCO = "motoristapro.db"

        @Volatile
        private var INSTANCIA: AppDatabase? = null

        /** Singleton thread-safe. Use sempre o applicationContext (MotoristaApp.database). */
        fun get(context: Context): AppDatabase =
            INSTANCIA ?: synchronized(this) {
                INSTANCIA ?: construir(context.applicationContext).also { INSTANCIA = it }
            }

        private fun construir(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NOME_BANCO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(SEED)
                // NÃO use fallbackToDestructiveMigration(): apagaria o histórico do motorista.
                .build()

        /** v1 -> v2: tarifa mínima desejada por corrida. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE configuracoes " +
                        "ADD COLUMN tarifa_minima_centavos INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v2 -> v3: meta semanal, dados do veículo e jornadas (cronômetro + GPS). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE configuracoes ADD COLUMN meta_lucro_semanal_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE configuracoes ADD COLUMN veiculo_nome TEXT")
                db.execSQL("ALTER TABLE configuracoes ADD COLUMN consumo_km_l_x100 INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE configuracoes ADD COLUMN preco_litro_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `jornadas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`inicio_em` INTEGER NOT NULL, " +
                        "`fim_em` INTEGER, " +
                        "`metros_gps` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_jornadas_inicio_em` ON `jornadas` (`inicio_em`)")
            }
        }

        /** v3 -> v4: cadastra o iFood (INSERT OR IGNORE: nome é UNIQUE). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO plataformas (nome, cor_hex, ativa) VALUES ('iFood', '#EA1D2C', 1)")
            }
        }

        /** v4 -> v5: histórico de ofertas + custos fixos mensais. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ofertas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recebida_em` INTEGER NOT NULL, " +
                        "`plataforma` TEXT NOT NULL, " +
                        "`valor_centavos` INTEGER NOT NULL, " +
                        "`metros` INTEGER NOT NULL, " +
                        "`minutos` INTEGER NOT NULL, " +
                        "`paradas` INTEGER NOT NULL DEFAULT 0, " +
                        "`classificacao` TEXT NOT NULL, " +
                        "`registrada` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ofertas_recebida_em` ON `ofertas` (`recebida_em`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custos_fixos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`nome` TEXT NOT NULL, " +
                        "`valor_mensal_centavos` INTEGER NOT NULL, " +
                        "`ativo` INTEGER NOT NULL DEFAULT 1)"
                )
            }
        }

        /** v5 -> v6: dados do perfil do motorista. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE configuracoes ADD COLUMN nome_motorista TEXT")
                db.execSQL("ALTER TABLE configuracoes ADD COLUMN telefone TEXT")
                db.execSQL("ALTER TABLE configuracoes ADD COLUMN cidade TEXT")
            }
        }

        /**
         * Fecha e descarta a instância (usado antes de restaurar um backup).
         * Depois disso o processo deve ser reiniciado.
         */
        fun fechar() {
            synchronized(this) {
                INSTANCIA?.close()
                INSTANCIA = null
            }
        }

        /** Dados iniciais — executado só quando o arquivo do banco é criado pela primeira vez. */
        private val SEED = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """
                    INSERT INTO plataformas (nome, cor_hex, ativa) VALUES
                        ('Uber', '#000000', 1),
                        ('99', '#FFDD00', 1),
                        ('inDrive', '#C1F11D', 1),
                        ('iFood', '#EA1D2C', 1),
                        ('Particular', '#1E88E5', 1)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO configuracoes (
                        id,
                        meta_lucro_diario_centavos,
                        meta_lucro_mensal_centavos,
                        custo_km_centavos,
                        tarifa_minima_centavos,
                        dias_trabalho_mes,
                        meta_lucro_semanal_centavos,
                        consumo_km_l_x100,
                        preco_litro_centavos
                    ) VALUES (1, 20000, 500000, 45, 800, 26, 120000, 1200, 600)
                    """.trimIndent()
                )
            }
        }
    }
}
