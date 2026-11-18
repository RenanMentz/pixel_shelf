package com.pixelshelf.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConquistaDao {
    /** Das mais comuns para as mais raras (ordem estável ao marcar/desmarcar). */
    @Query("SELECT * FROM conquistas WHERE jogoId = :jogoId ORDER BY raridade DESC, id")
    fun observarDoJogo(jogoId: Int): Flow<List<Conquista>>

    @Insert suspend fun inserirTodas(conquistas: List<Conquista>)

    @Query("UPDATE conquistas SET obtida = :obtida WHERE id = :id")
    suspend fun definirObtida(id: Int, obtida: Boolean)
}
