package com.pixelshelf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.data.steam.JogoNaoEncontradoException
import com.pixelshelf.data.steam.JogoSteam
import com.pixelshelf.data.steam.SteamRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Importação de um jogo da Steam no formulário.
 * Fica no ViewModel para sobreviver à rotação: a requisição em andamento não se perde e as
 * conquistas importadas continuam guardadas até o jogo ser salvo.
 */
class GameFormViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SteamRepository()

    private val _carregando = MutableStateFlow(false)
    val carregando: StateFlow<Boolean> = _carregando

    /** Último jogo importado (as conquistas dele são gravadas junto ao salvar). */
    var importado: JogoSteam? = null
        private set

    sealed interface Evento {
        data class Importado(val jogo: JogoSteam) : Evento
        data class Erro(val mensagem: String) : Evento
    }

    // Channel: cada evento é entregue uma vez só (não repete o preenchimento ao girar a tela)
    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    fun importar(appId: Int) {
        if (_carregando.value) return
        viewModelScope.launch {
            _carregando.value = true
            val evento = try {
                // com a conta Steam vinculada, as conquistas já obtidas vêm marcadas
                val steamId = AppDatabase.get(getApplication()).usuarioDao()
                    .buscarPorId(SessaoUsuario.idLogado(getApplication()))?.steamId
                val jogo = repo.detalhes(appId, steamId)
                importado = jogo
                Evento.Importado(jogo)
            } catch (e: JogoNaoEncontradoException) {
                Evento.Erro("Esse jogo não está disponível na loja da Steam.")
            } catch (e: IOException) {
                Evento.Erro("Sem conexão com a Steam. Verifique a internet e tente de novo.")
            } catch (e: HttpException) {
                Evento.Erro("A Steam não respondeu (erro ${e.code()}). Tente de novo.")
            }
            _carregando.value = false
            _eventos.send(evento)
        }
    }
}
