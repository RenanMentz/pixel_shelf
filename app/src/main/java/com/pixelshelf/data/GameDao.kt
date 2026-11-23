package com.pixelshelf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query(
        """
        SELECT g.*,
            (SELECT COUNT(*) FROM conquistas c WHERE c.jogoId = g.id) AS totalConquistas,
            (SELECT COUNT(*) FROM conquistas c WHERE c.jogoId = g.id AND c.obtida = 1) AS conquistasObtidas
        FROM games g
        """
    )
    fun listarResumos(): Flow<List<GameResumo>>

    @Query("SELECT DISTINCT plataforma FROM games ORDER BY plataforma COLLATE NOCASE")
    fun listarPlataformas(): Flow<List<String>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observarPorId(id: Int): Flow<Game?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun buscarPorId(id: Int): Game?

    /** Usado na regra de negócio que impede jogo duplicado na mesma plataforma. */
    @Query(
        """
        SELECT COUNT(*) FROM games
        WHERE titulo = :titulo COLLATE NOCASE AND plataforma = :plataforma AND id != :ignorarId
        """
    )
    suspend fun contarDuplicados(titulo: String, plataforma: String, ignorarId: Int): Int

    @Query("SELECT COUNT(*) FROM games")
    suspend fun contar(): Int

    @Query("UPDATE games SET favorito = :favorito WHERE id = :id")
    suspend fun definirFavorito(id: Int, favorito: Boolean)

    @Insert suspend fun inserir(game: Game): Long
    @Update suspend fun atualizar(game: Game)
    @Delete suspend fun excluir(game: Game)
}
