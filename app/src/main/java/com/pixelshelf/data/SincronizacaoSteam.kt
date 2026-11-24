package com.pixelshelf.data

import android.content.Context
import androidx.room.withTransaction
import com.pixelshelf.data.steam.ContaSteamRepository
import com.pixelshelf.data.steam.JogoDaConta
import com.pixelshelf.data.steam.JogoNaoEncontradoException
import com.pixelshelf.data.steam.JogoSteam
import com.pixelshelf.data.steam.PerfilPrivadoException
import com.pixelshelf.data.steam.SituacaoConquistas
import com.pixelshelf.data.steam.SteamRepository
import retrofit2.HttpException
import java.io.IOException

/** Resultado da importação de jogos da conta Steam. */
data class ResultadoImportacao(val importados: Int, val jaNaBiblioteca: Int, val falharam: Int)

/** Resultado da sincronização das conquistas. */
data class ResultadoSincronizacao(
    val jogosSincronizados: Int,
    val conquistasAtualizadas: Int,
    val horasAtualizadas: Int,
    val semCorrespondencia: Int,  // jogos de PC que não estão na conta Steam
    /** A Steam mostrou os jogos, mas não as conquistas ("Detalhes de jogos" privados). */
    val conquistasPrivadas: Boolean = false
)

/**
 * Regras de negócio da conta Steam vinculada:
 * - Jogo importado entra como PC; fica "Jogando" (sem nota: o usuário avalia depois) se tem
 *   horas ou alguma conquista obtida, senão "Na fila". Jogo que já está na biblioteca não é
 *   importado de novo.
 * - A sincronização liga cada jogo de PC ao jogo da conta (pelo id da Steam ou pelo nome),
 *   marca/desmarca as conquistas conforme a Steam e só aumenta as horas jogadas (nunca diminui
 *   o que o usuário registrou). Conquistas criadas manualmente não são alteradas.
 */
class SincronizacaoSteam(context: Context) {

    private val db = AppDatabase.get(context)
    private val steam = SteamRepository()
    private val conta = ContaSteamRepository()

    /** Jogos da conta que ainda não estão na biblioteca do usuário (por id da Steam ou nome). */
    suspend fun appIdsNaBiblioteca(usuarioId: Int): Pair<Set<Int>, Set<String>> {
        val jogos = db.gameDao().listarDoUsuario(usuarioId)
        return jogos.mapNotNull { it.steamAppId }.toSet() to
            jogos.filter { it.plataforma == PC }.map { normalizar(it.titulo) }.toSet()
    }

    suspend fun importar(
        usuarioId: Int,
        steamId: String,
        selecionados: List<JogoDaConta>,
        aoProgredir: suspend (feitos: Int, total: Int, atual: String) -> Unit
    ): ResultadoImportacao {
        var importados = 0
        var repetidos = 0
        var falhas = 0
        selecionados.forEachIndexed { i, jogo ->
            aoProgredir(i, selecionados.size, jogo.nome)
            val (ids, nomes) = appIdsNaBiblioteca(usuarioId)
            if (jogo.appId in ids || normalizar(jogo.nome) in nomes) { repetidos++; return@forEachIndexed }

            val detalhes: JogoSteam? = try {
                steam.detalhes(jogo.appId, steamId)
            } catch (e: JogoNaoEncontradoException) {
                null   // jogo fora da loja: entra só com o que a conta informa
            } catch (e: IOException) {
                falhas++; return@forEachIndexed
            } catch (e: HttpException) {
                falhas++; return@forEachIndexed
            }
            gravar(usuarioId, jogo, detalhes)
            importados++
        }
        aoProgredir(selecionados.size, selecionados.size, "")
        return ResultadoImportacao(importados, repetidos, falhas)
    }

    private suspend fun gravar(usuarioId: Int, jogo: JogoDaConta, d: JogoSteam?) {
        val conquistas = if (d?.situacaoConquistas == SituacaoConquistas.IMPORTADAS) d.conquistas else emptyList()
        // quem esconde o tempo de jogo na Steam aparece com 0 h: conquista obtida também conta como "já jogou"
        val jaJogou = jogo.horas > 0f || conquistas.any { it.obtida }
        val novo = Game(
            usuarioId = usuarioId,
            titulo = d?.titulo ?: jogo.nome,
            plataforma = PC,
            status = if (jaJogou) GameStatus.JOGANDO else GameStatus.NA_FILA,
            nota = null,
            horasJogadas = jogo.horas,
            capaUrl = d?.capaUrl,
            dataLancamento = d?.dataLancamento,
            desenvolvedora = d?.desenvolvedora,
            genero = d?.genero,
            descricao = d?.descricao,
            steamAppId = jogo.appId
        )
        db.withTransaction {
            val id = db.gameDao().inserir(novo).toInt()
            if (conquistas.isNotEmpty()) {
                db.conquistaDao().inserirTodas(conquistas.map {
                    Conquista(
                        jogoId = id, nome = it.nome, descricao = it.descricao,
                        iconeUrl = it.iconeUrl, raridade = it.raridade, obtida = it.obtida
                    )
                })
            }
        }
    }

    /** Atualiza conquistas e horas dos jogos de PC da biblioteca com os dados da conta Steam. */
    suspend fun sincronizar(usuarioId: Int, steamId: String): ResultadoSincronizacao {
        val daConta = conta.jogos(steamId)   // PerfilPrivadoException se a lista for privada
        val porId = daConta.associateBy { it.appId }
        val porNome = daConta.associateBy { normalizar(it.nome) }

        var sincronizados = 0
        var conquistasAlteradas = 0
        var horasAlteradas = 0
        var semCorrespondencia = 0
        var conquistasPrivadas = false
        for (g in db.gameDao().listarDoUsuario(usuarioId)) {
            if (g.plataforma != PC) continue
            val jogo = g.steamAppId?.let(porId::get) ?: porNome[normalizar(g.titulo)]
            if (jogo == null) { semCorrespondencia++; continue }
            if (g.steamAppId != jogo.appId) db.gameDao().definirSteamAppId(g.id, jogo.appId)

            if (jogo.horas > g.horasJogadas) {
                db.gameDao().definirHoras(g.id, jogo.horas)
                horasAlteradas++
            }

            // o dono da chave vê a própria lista de jogos mesmo com a privacidade fechada,
            // mas as conquistas só aparecem com os "Detalhes de jogos" públicos: nesse caso
            // as horas continuam sendo sincronizadas e as conquistas ficam como estão
            val progresso = if (conquistasPrivadas) null else try {
                conta.conquistasDoJogador(steamId, jogo.appId)
            } catch (e: PerfilPrivadoException) {
                conquistasPrivadas = true
                null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null   // falha pontual em um jogo: segue com os outros
            }
            if (progresso != null) {
                // liga pelo nome traduzido (as conquistas importadas foram gravadas em pt-BR)
                val obtidaPorNome = progresso.filter { it.nome != null }
                    .associate { normalizar(it.nome!!) to it.obtida }
                for (c in db.conquistaDao().listarDoJogo(g.id)) {
                    val obtida = obtidaPorNome[normalizar(c.nome)] ?: continue // manual: não mexe
                    if (obtida != c.obtida) {
                        db.conquistaDao().definirObtida(c.id, obtida)
                        conquistasAlteradas++
                    }
                }
            }
            sincronizados++
        }
        return ResultadoSincronizacao(
            sincronizados, conquistasAlteradas, horasAlteradas, semCorrespondencia, conquistasPrivadas
        )
    }

    companion object {
        private const val PC = "PC"

        /** Compara nomes ignorando maiúsculas, acentos de marca (™ ®) e espaços extras. */
        fun normalizar(nome: String) =
            nome.lowercase().replace(Regex("[™®©]"), "").replace(Regex("\\s+"), " ").trim()
    }
}
