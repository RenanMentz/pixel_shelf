package com.pixelshelf.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun buscarPorEmail(email: String): Usuario?

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun buscarPorId(id: Int): Usuario?

    @Query("SELECT * FROM usuarios WHERE id = :id")
    fun observarPorId(id: Int): Flow<Usuario?>

    /** Regra de negócio: uma conta Steam só pode estar vinculada a um usuário. */
    @Query("SELECT * FROM usuarios WHERE steamId = :steamId LIMIT 1")
    suspend fun buscarPorSteamId(steamId: String): Usuario?

    /** null desvincula. */
    @Query("UPDATE usuarios SET steamId = :steamId WHERE id = :id")
    suspend fun definirSteamId(id: Int, steamId: String?)

    @Insert
    suspend fun inserir(usuario: Usuario): Long
}
