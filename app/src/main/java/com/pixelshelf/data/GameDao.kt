package com.pixelshelf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY titulo COLLATE NOCASE")
    fun listar(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun buscarPorId(id: Int): Game?

    @Insert suspend fun inserir(game: Game)
    @Update suspend fun atualizar(game: Game)
    @Delete suspend fun excluir(game: Game)
}
