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
import com.motoristapro.data.local.dao.ManutencaoDao
import com.motoristapro.data.local.dao.OfertaDao
import com.motoristapro.data.local.dao.PlataformaDao
import com.motoristapro.data.local.dao.PerfilCustoDao
import com.motoristapro.data.local.dao.SincronizacaoDao
import com.motoristapro.data.local.dao.RelatorioDao
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.PerfilCusto
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.data.local.entity.Plataforma

/** Versão atual do esquema do banco (constante de topo: pode ser usada na anotação). */
const val VERSAO_BANCO = 13

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
 *  7 -> perfil de custo do veículo (assistente de custo real por km)
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
        CustoFixo::class,
        PerfilCusto::class,
        ItemManutencao::class
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
    abstract fun perfilCustoDao(): PerfilCustoDao
    abstract fun manutencaoDao(): ManutencaoDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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
         * v10 -> v11: a Jornada passou a dividir o turno por estado (offline,
         * aguardando, buscando, esperando, em viagem) e a contar quanto tempo
         * cada aplicativo ficou online.
         */
        /**
         * v11 -> v12: o botão Pausar da jornada.
         *
         * Uma coluna só, com o tempo total que o turno ficou pausado. O
         * cronômetro passou a descontar isso, senão a hora do almoço entrava
         * como hora trabalhada e estragava o R$/hora.
         */
        /**
         * v12 -> v13: controle de manutenção por quilometragem.
         *
         * Tabela nova e vazia: nada é inventado para quem já usa o app. O
         * motorista cadastra o item, de quantos em quantos km se faz e o
         * hodômetro da última troca — sem esses três números o aviso mentiria.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manutencoes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`nome` TEXT NOT NULL, " +
                        "`intervalo_km` INTEGER NOT NULL, " +
                        "`odometro_ultima_km` INTEGER NOT NULL, " +
                        "`atualizado_em` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jornadas ADD COLUMN seg_pausados INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "seg_offline", "seg_aguardando", "seg_buscando", "seg_esperando",
                    "seg_em_viagem", "seg_uber", "seg_99", "seg_ifood", "seg_indrive",
                    "pausada_em"
                ).forEach { coluna ->
                    db.execSQL("ALTER TABLE jornadas ADD COLUMN $coluna INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        /**
         * v9 -> v10: o historico passou a mostrar o cartao completo da oferta, e
         * para isso guarda o que o leitor ja lia e jogava fora: a nota e os dois
         * enderecos.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ofertas ADD COLUMN nota REAL")
                db.execSQL("ALTER TABLE ofertas ADD COLUMN origem TEXT")
                db.execSQL("ALTER TABLE ofertas ADD COLUMN destino TEXT")
            }
        }

        /**
         * v8 -> v9: o assistente virou o unico lugar de digitar os dados. Ele
         * guarda o nome do veiculo e passa a criar os custos fixos sozinho —
         * daí a coluna que separa o que veio dele do que o motorista digitou.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE perfil_custo ADD COLUMN veiculo_nome TEXT")
                db.execSQL(
                    "ALTER TABLE custos_fixos ADD COLUMN origem TEXT NOT NULL DEFAULT 'MANUAL'"
                )
            }
        }

        /**
         * v7 -> v8: o assistente passou a perguntar O QUE o motorista roda e a
         * aceitar o IPVA em reais por ano (antes só em %, e o % era cortado em
         * 100% sem avisar).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE perfil_custo ADD COLUMN tipo_trabalho TEXT NOT NULL DEFAULT 'PASSAGEIROS'"
                )
                db.execSQL("ALTER TABLE perfil_custo ADD COLUMN ipva_em_reais INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE perfil_custo ADD COLUMN ipva_anual_centavos INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 -> v7: tabela do assistente de custo (uma linha, criada em branco). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS perfil_custo (
                        id INTEGER NOT NULL PRIMARY KEY,
                        forma TEXT NOT NULL DEFAULT 'QUITADO',
                        tipo_veiculo TEXT NOT NULL DEFAULT 'MOTO',
                        valor_veiculo_centavos INTEGER NOT NULL DEFAULT 0,
                        parcela_mensal_centavos INTEGER NOT NULL DEFAULT 0,
                        aluguel_mensal_centavos INTEGER NOT NULL DEFAULT 0,
                        seguro_mensal_centavos INTEGER NOT NULL DEFAULT 0,
                        ipva_percent_x100 INTEGER NOT NULL DEFAULT 400,
                        desvalorizacao_anual_x100 INTEGER NOT NULL DEFAULT 1000,
                        outros_mensais_centavos INTEGER NOT NULL DEFAULT 0,
                        revisao_centavos INTEGER NOT NULL DEFAULT 0,
                        intervalo_revisao_km INTEGER NOT NULL DEFAULT 0,
                        troca_oleo_centavos INTEGER NOT NULL DEFAULT 0,
                        intervalo_oleo_km INTEGER NOT NULL DEFAULT 0,
                        jogo_pneus_centavos INTEGER NOT NULL DEFAULT 0,
                        duracao_pneus_km INTEGER NOT NULL DEFAULT 0,
                        outros_desgaste_centavos INTEGER NOT NULL DEFAULT 0,
                        outros_desgaste_km INTEGER NOT NULL DEFAULT 0,
                        combustivel TEXT NOT NULL DEFAULT 'GASOLINA',
                        preco_litro_centavos INTEGER NOT NULL DEFAULT 0,
                        consumo_x100 INTEGER NOT NULL DEFAULT 0,
                        km_por_dia INTEGER NOT NULL DEFAULT 0,
                        dias_por_semana INTEGER NOT NULL DEFAULT 6,
                        horas_por_dia INTEGER NOT NULL DEFAULT 10,
                        meta_lucro_mensal_centavos INTEGER NOT NULL DEFAULT 0,
                        calculado_em INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
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
