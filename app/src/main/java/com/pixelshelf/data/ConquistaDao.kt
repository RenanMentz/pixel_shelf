package com.pixelshelf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConquistaDao {
    /** Das mais comuns para as mais raras (ordem estável ao marcar/desmarcar). */
    @Query("SELECT * FROM conquistas WHERE jogoId = :jogoId ORDER BY raridade DESC, id")
    fun observarDoJogo(jogoId: Int): Flow<List<Conquista>>

    @Query("SELECT * FROM conquistas WHERE jogoId = :jogoId")
    suspend fun listarDoJogo(jogoId: Int): List<Conquista>

    @Insert suspend fun inserirTodas(conquistas: List<Conquista>)
    @Insert suspend fun inserir(conquista: Conquista)
    @Delete suspend fun excluir(conquista: Conquista)

    /** Regra de negócio: um jogo não pode ter duas conquistas com o mesmo nome. */
    @Query("SELECT COUNT(*) FROM conquistas WHERE jogoId = :jogoId AND nome = :nome COLLATE NOCASE")
    suspend fun contarPorNome(jogoId: Int, nome: String): Int

    @Query("UPDATE conquistas SET obtida = :obtida WHERE id = :id")
    suspend fun definirObtida(id: Int, obtida: Boolean)
}
