package com.pixelshelf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.GameResumo
import com.pixelshelf.data.SessaoUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class Ordenacao { NOME, NOTA }

/** Resultado já filtrado e ordenado + se existe algum jogo cadastrado. */
data class Biblioteca(val jogos: List<GameResumo>, val temJogos: Boolean)

/**
 * Guarda o estado da biblioteca (pesquisa, filtro e ordenação).
 * Por ficar no ViewModel, o estado sobrevive à rotação da tela.
 * Mostra só os jogos do usuário logado.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).gameDao()
    private val usuarioId = SessaoUsuario.idLogado(app)

    val busca = MutableStateFlow("")
    val plataforma = MutableStateFlow<String?>(null) // null = todas
    val ordenacao = MutableStateFlow(Ordenacao.NOME)

    /** null enquanto o banco ainda não respondeu. */
    val plataformas: StateFlow<List<String>?> = dao.listarPlataformas(usuarioId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val biblioteca: StateFlow<Biblioteca?> =
        combine(dao.listarResumos(usuarioId), busca, plataforma, ordenacao) { todos, texto, plat, ordem ->
            val filtrados = todos
                .filter { plat == null || it.game.plataforma == plat }
                .filter { texto.isBlank() || it.game.titulo.contains(texto.trim(), ignoreCase = true) }
            val porNome = compareBy(String.CASE_INSENSITIVE_ORDER) { r: GameResumo -> r.game.titulo }
            val ordenados = when (ordem) {
                Ordenacao.NOME -> filtrados.sortedWith(porNome)
                // jogos sem nota (na fila) vão para o fim
                Ordenacao.NOTA -> filtrados.sortedWith(
                    compareByDescending<GameResumo> { it.game.nota ?: -1f }.then(porNome)
                )
            }
            Biblioteca(ordenados, todos.isNotEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
