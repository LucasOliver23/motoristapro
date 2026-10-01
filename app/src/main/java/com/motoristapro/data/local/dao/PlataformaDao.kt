package com.motoristapro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.motoristapro.data.local.entity.Plataforma
import kotlinx.coroutines.flow.Flow

@Dao
interface PlataformaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(plataforma: Plataforma): Long

    @Update
    suspend fun atualizar(plataforma: Plataforma)

    @Query("SELECT * FROM plataformas WHERE ativa = 1 ORDER BY nome")
    fun observarAtivas(): Flow<List<Plataforma>>

    @Query("SELECT * FROM plataformas WHERE nome = :nome COLLATE NOCASE LIMIT 1")
    suspend fun buscarPorNome(nome: String): Plataforma?

    @Query("SELECT * FROM plataformas ORDER BY nome")
    suspend fun listarTodas(): List<Plataforma>

    /** Desativar em vez de apagar: preserva o histórico de corridas. */
    @Query("UPDATE plataformas SET ativa = :ativa WHERE id = :id")
    suspend fun definirAtiva(id: Long, ativa: Boolean)
}
