package com.pixelshelf.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pixelshelf.data.GameResumo
import com.pixelshelf.data.GameStatus

/** Cria uma aba (GameListFragment) para "Todos", "Favoritos" e cada status de jogo. */
class StatusPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    /**
     * Uma aba da biblioteca.
     * status = null e favoritos = false significa "todos os jogos".
     */
    data class Aba(val titulo: String, val status: String? = null, val favoritos: Boolean = false) {
        fun aceita(r: GameResumo): Boolean =
            (status == null || r.game.status == status) && (!favoritos || r.game.favorito)
    }

    companion object {
        val ABAS = listOf(
            Aba("Todos"),
            Aba("Favoritos", favoritos = true),
            Aba("Jogando", GameStatus.JOGANDO),
            Aba("Zerado", GameStatus.ZERADO),
            Aba("Na fila", GameStatus.NA_FILA)
        )
    }

    override fun getItemCount() = ABAS.size

    override fun createFragment(position: Int): Fragment = GameListFragment.newInstance(position)
}
