package com.pixelshelf.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pixelshelf.data.GameStatus

/** Cria uma aba (GameListFragment) para cada status de jogo, mais a aba "Todos". */
class StatusPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    /** status = null significa "todos os jogos". */
    data class Aba(val titulo: String, val status: String?)

    companion object {
        val ABAS = listOf(
            Aba("Todos", null),
            Aba("Jogando", GameStatus.JOGANDO),
            Aba("Zerado", GameStatus.ZERADO),
            Aba("Na fila", GameStatus.NA_FILA)
        )
    }

    override fun getItemCount() = ABAS.size

    override fun createFragment(position: Int): Fragment =
        GameListFragment.newInstance(ABAS[position].status)
}
