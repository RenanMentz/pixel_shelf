package com.pixelshelf.data

import android.content.Context

/**
 * Guarda quem está logado.
 * - "Manter conectado" marcado: o id fica salvo em SharedPreferences e o login persiste.
 * - Desmarcado: o id fica só em memória e vale até o app ser fechado pelo sistema.
 */
object SessaoUsuario {

    const val NENHUM = -1
    private const val PREFS = "pixel_shelf"
    private const val CHAVE = "usuario_logado_id"

    @Volatile private var idEmMemoria = NENHUM

    fun idLogado(context: Context): Int {
        if (idEmMemoria != NENHUM) return idEmMemoria
        val salvo = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(CHAVE, NENHUM)
        idEmMemoria = salvo
        return salvo
    }

    fun entrar(context: Context, usuarioId: Int, manterConectado: Boolean) {
        idEmMemoria = usuarioId
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (manterConectado) prefs.putInt(CHAVE, usuarioId) else prefs.remove(CHAVE)
        prefs.apply()
    }

    fun sair(context: Context) {
        idEmMemoria = NENHUM
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(CHAVE).apply()
    }
}
