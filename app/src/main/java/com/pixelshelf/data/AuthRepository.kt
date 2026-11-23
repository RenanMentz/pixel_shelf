package com.pixelshelf.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Regras de cadastro e autenticação. */
class AuthRepository(context: Context) {

    private val dao = AppDatabase.get(context).usuarioDao()

    /** Cria a conta. Retorna o usuário criado, ou null se o e-mail já estiver cadastrado. */
    suspend fun cadastrar(nome: String, email: String, senha: String): Usuario? =
        withContext(Dispatchers.Default) {
            val emailNormalizado = email.trim().lowercase()
            if (dao.buscarPorEmail(emailNormalizado) != null) return@withContext null

            val salt = Senhas.gerarSalt()
            val novo = Usuario(
                nome = nome.trim(),
                email = emailNormalizado,
                senhaHash = Senhas.hash(senha, salt),
                salt = salt
            )
            val id = try {
                dao.inserir(novo).toInt()
            } catch (e: SQLiteConstraintException) {
                return@withContext null   // e-mail repetido (índice único)
            }
            novo.copy(id = id)
        }

    /** Retorna o usuário se e-mail e senha conferem, senão null. */
    suspend fun autenticar(email: String, senha: String): Usuario? =
        withContext(Dispatchers.Default) {
            val usuario = dao.buscarPorEmail(email.trim().lowercase()) ?: return@withContext null
            if (Senhas.confere(senha, usuario.salt, usuario.senhaHash)) usuario else null
        }
}
