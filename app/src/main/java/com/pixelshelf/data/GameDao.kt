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
        WHERE g.usuarioId = :usuarioId
        """
    )
    fun listarResumos(usuarioId: Int): Flow<List<GameResumo>>

    @Query("SELECT DISTINCT plataforma FROM games WHERE usuarioId = :usuarioId ORDER BY plataforma COLLATE NOCASE")
    fun listarPlataformas(usuarioId: Int): Flow<List<String>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observarPorId(id: Int): Flow<Game?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun buscarPorId(id: Int): Game?

    /** Regra de negócio: o mesmo usuário não pode ter o jogo duas vezes na mesma plataforma. */
    @Query(
        """
        SELECT COUNT(*) FROM games
        WHERE usuarioId = :usuarioId AND titulo = :titulo COLLATE NOCASE
            AND plataforma = :plataforma AND id != :ignorarId
        """
    )
    suspend fun contarDuplicados(usuarioId: Int, titulo: String, plataforma: String, ignorarId: Int): Int

    /** Jogos de antes da biblioteca por usuário (sem dono) passam para quem entrou. */
    @Query("UPDATE games SET usuarioId = :usuarioId WHERE usuarioId = 0")
    suspend fun assumirJogosSemDono(usuarioId: Int)

    @Query("SELECT * FROM games WHERE usuarioId = :usuarioId")
    suspend fun listarDoUsuario(usuarioId: Int): List<Game>

    @Query("UPDATE games SET steamAppId = :appId WHERE id = :id")
    suspend fun definirSteamAppId(id: Int, appId: Int)

    @Query("UPDATE games SET horasJogadas = :horas WHERE id = :id")
    suspend fun definirHoras(id: Int, horas: Float)

    @Query("UPDATE games SET favorito = :favorito WHERE id = :id")
    suspend fun definirFavorito(id: Int, favorito: Boolean)

    @Insert suspend fun inserir(game: Game): Long
    @Update suspend fun atualizar(game: Game)
    @Delete suspend fun excluir(game: Game)
}
