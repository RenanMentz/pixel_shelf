package com.pixelshelf.ui

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.data.SincronizacaoSteam
import com.pixelshelf.data.Usuario
import com.pixelshelf.data.steam.ContaSteamRepository
import com.pixelshelf.data.steam.IdentificacaoSteam
import com.pixelshelf.data.steam.PerfilNaoEncontradoException
import com.pixelshelf.data.steam.PerfilPrivadoException
import com.pixelshelf.data.steam.PerfilSteam
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Vinculação da conta Steam. As requisições ficam no ViewModel, então girar a tela não as
 * cancela nem repete.
 */
class ContaSteamViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).usuarioDao()
    private val conta = ContaSteamRepository()
    private val sincronizacao = SincronizacaoSteam(app)
    private val usuarioId = SessaoUsuario.idLogado(app)

    val temChave: Boolean get() = conta.temChave

    val usuario: StateFlow<Usuario?> = dao.observarPorId(usuarioId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Perfil carregado da Steam (nome e avatar) da conta vinculada. */
    private val _perfil = MutableStateFlow<PerfilSteam?>(null)
    val perfil: StateFlow<PerfilSteam?> = _perfil

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado

    sealed interface Evento {
        data class Mensagem(val texto: String) : Evento
        data class ErroCampo(val texto: String) : Evento   // mostrado no campo do link
        data class Sincronizado(val texto: String) : Evento
    }

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    /** Carrega nome e avatar do perfil vinculado (uma vez por tela). */
    fun carregarPerfil(steamId: String) {
        if (_perfil.value?.steamId == steamId || !temChave) return
        viewModelScope.launch {
            runCatching { conta.identificar(IdentificacaoSteam.Id(steamId)) }
                .onSuccess { _perfil.value = it }
        }
    }

    fun vincular(texto: String) {
        val id = IdentificacaoSteam.interpretar(texto)
        if (id == null) {
            enviar(Evento.ErroCampo("Cole o link do perfil, o SteamID ou o nome personalizado"))
            return
        }
        executar {
            val perfil = try {
                conta.identificar(id)
            } catch (e: PerfilNaoEncontradoException) {
                enviar(Evento.ErroCampo("Nenhum perfil Steam encontrado com esse link"))
                return@executar
            }
            // Regra de negócio: a mesma conta Steam não pode estar em dois usuários do app
            val dono = dao.buscarPorSteamId(perfil.steamId)
            if (dono != null && dono.id != usuarioId) {
                enviar(Evento.ErroCampo("Essa conta Steam já está vinculada a outro usuário do app"))
                return@executar
            }
            try {
                dao.definirSteamId(usuarioId, perfil.steamId)
            } catch (e: SQLiteConstraintException) {
                enviar(Evento.ErroCampo("Essa conta Steam já está vinculada a outro usuário do app"))
                return@executar
            }
            _perfil.value = perfil
            enviar(Evento.Mensagem(
                if (perfil.publico) "Conta Steam de ${perfil.nome} vinculada!"
                else "Conta vinculada, mas o perfil de ${perfil.nome} não está público: " +
                    "a Steam não vai mostrar seus jogos e conquistas."
            ))
        }
    }

    fun desvincular() = executar {
        dao.definirSteamId(usuarioId, null)
        _perfil.value = null
        enviar(Evento.Mensagem("Conta Steam desvinculada. Seus jogos continuam na biblioteca."))
    }

    fun sincronizar() {
        val steamId = usuario.value?.steamId ?: return
        executar {
            val r = try {
                sincronizacao.sincronizar(usuarioId, steamId)
            } catch (e: PerfilPrivadoException) {
                enviar(Evento.Sincronizado(MENSAGEM_PRIVADO))
                return@executar
            }
            val texto = if (r.jogosSincronizados == 0) {
                "Nenhum jogo de PC da biblioteca foi encontrado na sua conta Steam."
            } else buildString {
                append("✓ ${qtd(r.jogosSincronizados, "jogo sincronizado", "jogos sincronizados")}: ")
                if (r.conquistasPrivadas) {
                    append(if (r.horasAtualizadas > 0) "horas de ${qtd(r.horasAtualizadas, "jogo", "jogos")} atualizadas"
                    else "horas já estavam em dia")
                } else {
                    append(qtd(r.conquistasAtualizadas, "conquista atualizada", "conquistas atualizadas"))
                    if (r.horasAtualizadas > 0) {
                        append(" e horas de ${qtd(r.horasAtualizadas, "jogo", "jogos")} atualizadas")
                    }
                }
                append('.')
                if (r.semCorrespondencia > 0) {
                    append("\n${qtd(r.semCorrespondencia, "jogo de PC não está", "jogos de PC não estão")} na conta Steam.")
                }
                if (r.conquistasPrivadas) append("\n\n$MENSAGEM_CONQUISTAS_PRIVADAS")
            }
            enviar(Evento.Sincronizado(texto))
        }
    }

    /** Roda uma ação de rede mostrando o carregamento e tratando falta de conexão. */
    private fun executar(acao: suspend () -> Unit) {
        if (_ocupado.value) return
        viewModelScope.launch {
            _ocupado.value = true
            try {
                acao()
            } catch (e: IOException) {
                enviar(Evento.Mensagem("Sem conexão com a Steam. Verifique a internet e tente de novo."))
            } catch (e: HttpException) {
                val texto = if (e.code() == 401 || e.code() == 403) "A Steam recusou a chave configurada."
                else "A Steam não respondeu (erro ${e.code()}). Tente de novo."
                enviar(Evento.Mensagem(texto))
            } finally {
                _ocupado.value = false
            }
        }
    }

    private fun enviar(e: Evento) { _eventos.trySend(e) }

    private fun qtd(n: Int, singular: String, plural: String) = if (n == 1) "1 $singular" else "$n $plural"

    companion object {
        const val MENSAGEM_CONQUISTAS_PRIVADAS =
            "As conquistas não foram sincronizadas: na Steam, deixe os \"Detalhes de jogos\" " +
                "como Público (Perfil → Editar perfil → Privacidade) e sincronize de novo."
        const val MENSAGEM_PRIVADO =
            "A Steam não mostrou seus jogos: deixe o perfil e os \"Detalhes de jogos\" como " +
                "Público em Steam → Perfil → Editar perfil → Privacidade."
    }
}
