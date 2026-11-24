package com.pixelshelf.data.steam

import com.pixelshelf.BuildConfig
import retrofit2.HttpException
import kotlin.math.roundToInt

/** Perfil Steam vinculado ao usuário. */
data class PerfilSteam(
    val steamId: String,
    val nome: String,
    val avatarUrl: String?,
    val publico: Boolean
)

/** Jogo que o usuário tem na conta Steam. */
data class JogoDaConta(val appId: Int, val nome: String, val horas: Float) {
    val imagemUrl: String
        get() = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/capsule_231x87.jpg"
}

/** Progresso do jogador em uma conquista. */
data class ConquistaDoJogador(val apiName: String, val nome: String?, val obtida: Boolean)

/** O que o usuário digitou para identificar o perfil. */
sealed interface IdentificacaoSteam {
    data class Id(val steamId: String) : IdentificacaoSteam
    data class NomePersonalizado(val nome: String) : IdentificacaoSteam

    companion object {
        private val PERFIL_POR_ID = Regex("""steamcommunity\.com/profiles/(\d{17})""", RegexOption.IGNORE_CASE)
        private val PERFIL_POR_NOME = Regex("""steamcommunity\.com/id/([^/?#\s]+)""", RegexOption.IGNORE_CASE)
        private val STEAM_ID = Regex("""7656119\d{10}""")
        private val NOME = Regex("""[A-Za-z0-9_-]{2,32}""")

        /**
         * Aceita o link do perfil (…/profiles/SteamID ou …/id/nome), o SteamID64 ou só o
         * nome personalizado. Retorna null se o texto não tiver nenhum desses formatos.
         */
        fun interpretar(texto: String): IdentificacaoSteam? {
            val t = texto.trim()
            PERFIL_POR_ID.find(t)?.let { return Id(it.groupValues[1]) }
            PERFIL_POR_NOME.find(t)?.let { return NomePersonalizado(it.groupValues[1]) }
            if (STEAM_ID.matches(t)) return Id(t)
            if (NOME.matches(t)) return NomePersonalizado(t)
            return null
        }
    }
}

class PerfilNaoEncontradoException : Exception()
/** A Steam não mostra os dados: perfil ou "detalhes de jogos" não estão públicos. */
class PerfilPrivadoException : Exception()

/**
 * Dados da conta Steam do usuário (Steam Web API, precisa de chave).
 */
class ContaSteamRepository(
    private val chave: String = BuildConfig.STEAM_API_KEY
) {
    private val web get() = SteamRepository.web

    val temChave: Boolean get() = chave.isNotBlank()

    /** Encontra o perfil a partir do que o usuário digitou. */
    suspend fun identificar(id: IdentificacaoSteam): PerfilSteam {
        val steamId = when (id) {
            is IdentificacaoSteam.Id -> id.steamId
            is IdentificacaoSteam.NomePersonalizado -> {
                val r = web.resolverNome(chave, id.nome).response
                if (r?.success != 1 || r.steamid.isNullOrBlank()) throw PerfilNaoEncontradoException()
                r.steamid
            }
        }
        val p = web.perfis(chave, steamId).response?.players?.firstOrNull()
            ?: throw PerfilNaoEncontradoException()
        return PerfilSteam(
            steamId = p.steamid,
            nome = p.personaname?.ifBlank { null } ?: p.steamid,
            avatarUrl = p.avatarfull,
            publico = p.communityvisibilitystate == 3
        )
    }

    /** Jogos da conta, do mais jogado para o menos jogado. */
    suspend fun jogos(steamId: String): List<JogoDaConta> {
        val r = web.jogosDaConta(chave, steamId).response
        // a Steam devolve a resposta vazia (sem game_count) quando a lista é privada
        if (r?.total == null) throw PerfilPrivadoException()
        return r.games.orEmpty()
            .filter { !it.name.isNullOrBlank() }
            .map { JogoDaConta(it.appid, it.name!!.trim(), horas(it.minutosJogados ?: 0)) }
            .sortedWith(compareByDescending<JogoDaConta> { it.horas }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.nome })
    }

    /**
     * Progresso do jogador nas conquistas de um jogo. Lista vazia se o jogo não tem
     * conquistas; [PerfilPrivadoException] se a Steam não mostrar os dados.
     */
    suspend fun conquistasDoJogador(steamId: String, appId: Int): List<ConquistaDoJogador> =
        try {
            web.conquistasDoJogador(chave, steamId, appId).playerstats?.achievements.orEmpty()
                .map { ConquistaDoJogador(it.apiname, it.name, it.achieved == 1) }
        } catch (e: HttpException) {
            when (e.code()) {
                403 -> throw PerfilPrivadoException()
                400 -> emptyList()  // "Requested app has no stats"
                else -> throw e
            }
        }

    companion object {
        /** Minutos da Steam para horas com uma casa decimal. */
        fun horas(minutos: Int): Float = (minutos / 6f).roundToInt() / 10f
    }
}
