package com.motoristapro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.PerfilCusto
import com.motoristapro.data.local.entity.Veiculo
import com.motoristapro.data.local.entity.Plataforma

/** Tudo que vai e volta da nuvem. O histórico de ofertas fica só no celular (é grande e local). */
data class Pacote(
    val plataformas: List<Plataforma>,
    val corridas: List<Corrida>,
    val despesas: List<Despesa>,
    val jornadas: List<Jornada>,
    val custosFixos: List<CustoFixo>,
    val configuracao: Configuracao?,
    /** null = backup antigo, de antes da manutenção ir junto: aí as do celular ficam. */
    val manutencoes: List<ItemManutencao>? = null,
    /** As respostas do assistente de custo. null = backup antigo (ou assistente nunca usado). */
    val perfilCusto: PerfilCusto? = null,
    /** Os veículos (v14). null = backup de antes de ter vários: os do celular ficam. */
    val veiculos: List<Veiculo>? = null
) {
    /** Sem corrida, despesa, jornada nem manutenção: é celular recém-instalado (ou zerado). */
    val vazio: Boolean
        get() = corridas.isEmpty() && despesas.isEmpty() && jornadas.isEmpty() && manutencoes.isNullOrEmpty()
}

/** Leitura e escrita em bloco, usada só pela sincronização e pela restauração. */
@Dao
interface SincronizacaoDao {

    @Query("SELECT * FROM corridas ORDER BY inicio_em") suspend fun corridas(): List<Corrida>
    @Query("SELECT * FROM despesas ORDER BY data_em") suspend fun despesas(): List<Despesa>
    @Query("SELECT * FROM plataformas ORDER BY id") suspend fun plataformas(): List<Plataforma>
    @Query("SELECT * FROM jornadas ORDER BY inicio_em") suspend fun jornadas(): List<Jornada>
    @Query("SELECT * FROM custos_fixos ORDER BY id") suspend fun custosFixos(): List<CustoFixo>
    @Query("SELECT * FROM configuracoes WHERE id = 1") suspend fun configuracao(): Configuracao?
    @Query("SELECT * FROM manutencoes ORDER BY id") suspend fun manutencoes(): List<ItemManutencao>
    @Query("SELECT * FROM perfil_custo WHERE id = 1") suspend fun perfilCusto(): PerfilCusto?
    @Query("SELECT * FROM veiculos ORDER BY id") suspend fun veiculos(): List<Veiculo>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirPlataformas(itens: List<Plataforma>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirCorridas(itens: List<Corrida>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirDespesas(itens: List<Despesa>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirJornadas(itens: List<Jornada>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirCustosFixos(itens: List<CustoFixo>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirConfiguracao(item: Configuracao)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirManutencoes(itens: List<ItemManutencao>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirPerfilCusto(item: PerfilCusto)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirVeiculos(itens: List<Veiculo>)

    @Query("DELETE FROM corridas") suspend fun limparCorridas()
    @Query("DELETE FROM despesas") suspend fun limparDespesas()
    @Query("DELETE FROM jornadas") suspend fun limparJornadas()
    @Query("DELETE FROM custos_fixos") suspend fun limparCustosFixos()
    @Query("DELETE FROM plataformas") suspend fun limparPlataformas()
    @Query("DELETE FROM ofertas") suspend fun limparOfertas()
    @Query("DELETE FROM perfil_custo") suspend fun limparPerfilCusto()
    @Query("DELETE FROM configuracoes") suspend fun limparConfiguracao()
    @Query("DELETE FROM manutencoes") suspend fun limparManutencoes()
    @Query("DELETE FROM veiculos") suspend fun limparVeiculos()

    /**
     * Deixa o celular como se o app tivesse acabado de ser instalado.
     *
     * Usado quando OUTRA conta entra no mesmo aparelho: os dados são de quem
     * estava antes, e o motorista novo tem que começar do zero — inclusive o
     * custo por km e as metas, que são dele, não do celular.
     *
     * As plataformas ficam: Uber, 99 e iFood são catálogo do app, não dado de
     * ninguém, e apagá-las deixaria as corridas sem para onde apontar.
     */
    @Transaction
    suspend fun zerar() {
        limparCorridas()
        limparDespesas()
        limparJornadas()
        limparCustosFixos()
        limparOfertas()
        limparPerfilCusto()
        limparManutencoes()
        limparConfiguracao()
        inserirConfiguracao(Configuracao())
        // Volta a ter só o veículo 1, como num app recém-instalado.
        limparVeiculos()
        inserirVeiculos(listOf(Veiculo(id = Veiculo.PRIMEIRO, nome = "Meu veículo")))
    }

    @Transaction
    suspend fun exportar(): Pacote = Pacote(
        plataformas = plataformas(),
        corridas = corridas(),
        despesas = despesas(),
        jornadas = jornadas(),
        custosFixos = custosFixos(),
        configuracao = configuracao(),
        manutencoes = manutencoes(),
        perfilCusto = perfilCusto(),
        veiculos = veiculos()
    )

    /**
     * Substitui TUDO pelo conteúdo do pacote, numa transação só:
     * ou entra inteiro, ou nada muda. A ordem respeita a chave estrangeira
     * (corridas dependem de plataformas).
     */
    @Transaction
    suspend fun importar(p: Pacote) {
        limparCorridas()
        limparDespesas()
        limparJornadas()
        limparCustosFixos()
        limparPlataformas()

        inserirPlataformas(p.plataformas)
        inserirCorridas(p.corridas)
        inserirDespesas(p.despesas)
        inserirJornadas(p.jornadas)
        inserirCustosFixos(p.custosFixos)
        p.configuracao?.let { inserirConfiguracao(it) }
        // Backup antigo não traz estes: o que está no celular fica.
        p.manutencoes?.let {
            limparManutencoes()
            inserirManutencoes(it)
        }
        p.perfilCusto?.let {
            limparPerfilCusto()
            inserirPerfilCusto(it)
        }
        p.veiculos?.takeIf { it.isNotEmpty() }?.let {
            limparVeiculos()
            inserirVeiculos(it)
        }
    }
}
