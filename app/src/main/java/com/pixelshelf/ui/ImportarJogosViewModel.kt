package com.pixelshelf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.ResultadoImportacao
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.data.SincronizacaoSteam
import com.pixelshelf.data.steam.ContaSteamRepository
import com.pixelshelf.data.steam.JogoDaConta
import com.pixelshelf.data.steam.PerfilPrivadoException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Lista de jogos da conta Steam e importação dos selecionados (sobrevive à rotação). */
class ImportarJogosViewModel(app: Application) : AndroidViewModel(app) {

    private val usuarioId = SessaoUsuario.idLogado(app)
    private val conta = ContaSteamRepository()
    private val sincronizacao = SincronizacaoSteam(app)
    private var steamId: String? = null

    data class Item(
        val jogo: JogoDaConta,
        val naBiblioteca: Boolean,
        val selecionado: Boolean,
        val tempoOculto: Boolean   // a conta esconde o tempo de jogo (todos vêm com 0 h)
    )
    data class Progresso(val feitos: Int, val total: Int, val atual: String)

    sealed interface Estado {
        data object Carregando : Estado
        data class Erro(val mensagem: String) : Estado
        data class Pronto(val jogos: List<JogoDaConta>, val naBiblioteca: Set<Int>) : Estado {
            val tempoOculto: Boolean get() = jogos.all { it.horas == 0f }
        }
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Carregando)
    val estado: StateFlow<Estado> = _estado

    val filtro = MutableStateFlow("")
    private val selecionados = MutableStateFlow<Set<Int>>(emptySet())
    val quantidadeSelecionada: StateFlow<Int> = selecionados.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Lista filtrada pela pesquisa, com a marcação de cada jogo. */
    val itens: StateFlow<List<Item>> = combine(_estado, filtro, selecionados) { e, f, sel ->
        if (e !is Estado.Pronto) return@combine emptyList()
        e.jogos.filter { f.isBlank() || it.nome.contains(f.trim(), ignoreCase = true) }
            .map { Item(it, it.appId in e.naBiblioteca, it.appId in sel, e.tempoOculto) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _progresso = MutableStateFlow<Progresso?>(null)
    val progresso: StateFlow<Progresso?> = _progresso

    private val _concluido = Channel<ResultadoImportacao>(Channel.BUFFERED)
    val concluido = _concluido.receiveAsFlow()

    init { carregar() }

    fun carregar() {
        _estado.value = Estado.Carregando
        viewModelScope.launch {
            val id = AppDatabase.get(getApplication()).usuarioDao().buscarPorId(usuarioId)?.steamId
            if (id == null) {
                _estado.value = Estado.Erro("Vincule sua conta Steam primeiro.")
                return@launch
            }
            steamId = id
            _estado.value = try {
                val jogos = conta.jogos(id)
                val (ids, nomes) = sincronizacao.appIdsNaBiblioteca(usuarioId)
                val naBiblioteca = jogos.filter {
                    it.appId in ids || SincronizacaoSteam.normalizar(it.nome) in nomes
                }.map { it.appId }.toSet()
                if (jogos.isEmpty()) Estado.Erro("Sua conta Steam não tem jogos.")
                else Estado.Pronto(jogos, naBiblioteca)
            } catch (e: PerfilPrivadoException) {
                Estado.Erro(ContaSteamViewModel.MENSAGEM_PRIVADO)
            } catch (e: IOException) {
                Estado.Erro("Sem conexão com a Steam. Verifique a internet e tente de novo.")
            } catch (e: HttpException) {
                Estado.Erro("A Steam não respondeu (erro ${e.code()}). Tente de novo.")
            }
        }
    }

    fun alternar(item: Item) {
        if (item.naBiblioteca || _progresso.value != null) return
        val id = item.jogo.appId
        selecionados.value = selecionados.value.let { if (id in it) it - id else it + id }
    }

    fun importar() {
        val e = _estado.value as? Estado.Pronto ?: return
        val id = steamId ?: return
        val escolhidos = e.jogos.filter { it.appId in selecionados.value }
        if (escolhidos.isEmpty() || _progresso.value != null) return
        viewModelScope.launch {
            _progresso.value = Progresso(0, escolhidos.size, escolhidos.first().nome)
            val r = sincronizacao.importar(usuarioId, id, escolhidos) { feitos, total, atual ->
                _progresso.value = Progresso(feitos, total, atual)
            }
            _progresso.value = null
            selecionados.value = emptySet()
            _concluido.send(r)
        }
    }
}
