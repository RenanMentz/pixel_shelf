package com.pixelshelf.data.steam

import androidx.core.text.HtmlCompat
import com.pixelshelf.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Resultado da busca por nome. */
data class ResultadoSteam(val appId: Int, val nome: String, val imagemUrl: String?)

/** Conquista vinda da Steam, ainda sem jogo no banco. */
data class ConquistaSteam(
    val nome: String,
    val descricao: String,
    val iconeUrl: String?,
    val raridade: Float?,
    val apiName: String? = null,   // id interno na Steam (liga com as conquistas do jogador)
    val obtida: Boolean = false    // já desbloqueada na conta Steam vinculada
)

enum class SituacaoConquistas {
    IMPORTADAS,      // lista completa carregada
    SEM_CHAVE,       // jogo tem conquistas, mas não há chave da Steam configurada
    CHAVE_INVALIDA,  // a Steam recusou a chave
    FALHA,           // erro ao buscar as conquistas (o resto do jogo veio)
    NENHUMA          // o jogo não tem conquistas na Steam
}

/** Dados do jogo já convertidos para o formato do app. */
data class JogoSteam(
    val appId: Int,
    val titulo: String,
    val capaUrl: String?,
    val dataLancamento: String?,  // ISO yyyy-MM-dd
    val desenvolvedora: String?,
    val genero: String?,
    val descricao: String?,
    val totalConquistasSteam: Int,
    val conquistas: List<ConquistaSteam>,
    val situacaoConquistas: SituacaoConquistas,
    /** Situação do progresso do usuário; null quando não há conta Steam vinculada. */
    val situacaoConta: SituacaoConta? = null
) {
    val conquistasObtidas: Int get() = conquistas.count { it.obtida }
}

enum class SituacaoConta {
    SINCRONIZADA,    // conquistas desbloqueadas na conta já vêm marcadas
    PERFIL_PRIVADO,  // a Steam não mostrou o progresso (perfil ou detalhes de jogos privados)
    SEM_PROGRESSO    // não foi possível ler o progresso agora
}

/** Jogo inexistente ou indisponível na loja. */
class JogoNaoEncontradoException : Exception()

/**
 * Acesso à Steam com Retrofit. Converte os DTOs da API nos objetos usados pelo app.
 */
class SteamRepository(
    private val chave: String = BuildConfig.STEAM_API_KEY
) {

    val temChave: Boolean get() = chave.isNotBlank()

    suspend fun buscar(termo: String): List<ResultadoSteam> =
        loja.buscar(termo.trim()).items.orEmpty()
            .filter { it.type == "app" && !it.name.isNullOrBlank() }
            .map { ResultadoSteam(it.id, it.name!!, it.imagemPequena) }

    /**
     * Detalhes do jogo. Com [steamIdUsuario] (conta vinculada), as conquistas que o usuário
     * já desbloqueou na Steam vêm marcadas.
     */
    suspend fun detalhes(appId: Int, steamIdUsuario: String? = null): JogoSteam = coroutineScope {
        // as consultas são independentes, então rodam em paralelo
        val detalhesAsync = async { loja.detalhes(appId)[appId.toString()] }
        val capaAsync = async { escolherCapa(appId) }
        val conquistasAsync = async { carregarConquistas(appId) }
        val progressoAsync = if (steamIdUsuario != null && temChave) async {
            runCatching { ContaSteamRepository(chave).conquistasDoJogador(steamIdUsuario, appId) }
        } else null

        val envelope = detalhesAsync.await()
        val d = envelope?.data
        if (envelope?.success != true || d == null || d.name.isNullOrBlank()) {
            throw JogoNaoEncontradoException()
        }
        val total = d.achievements?.total ?: 0
        val (lista, situacao) = conquistasAsync.await()

        // marca o que o usuário já desbloqueou (liga pelo id interno da conquista)
        val progresso = progressoAsync?.await()
        val obtidas = progresso?.getOrNull()?.filter { it.obtida }?.mapNotNull { it.apiName }?.toSet()
        val conquistas = if (obtidas == null) lista else lista.map { it.copy(obtida = it.apiName in obtidas) }
        val situacaoConta = when {
            progresso == null || lista.isEmpty() -> null
            progresso.isSuccess -> SituacaoConta.SINCRONIZADA
            progresso.exceptionOrNull() is PerfilPrivadoException -> SituacaoConta.PERFIL_PRIVADO
            else -> SituacaoConta.SEM_PROGRESSO
        }

        JogoSteam(
            appId = appId,
            titulo = d.name.trim(),
            capaUrl = capaAsync.await() ?: d.imagemCabecalho,
            dataLancamento = if (d.lancamento?.emBreve == true) null else dataIso(d.lancamento?.date),
            desenvolvedora = d.developers?.firstOrNull()?.trim()?.ifEmpty { null },
            genero = d.genres.orEmpty().mapNotNull { it.description?.trim() }
                .filter { it.isNotEmpty() }.take(3).joinToString(", ").ifEmpty { null },
            descricao = d.descricaoCurta?.let {
                HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            }?.ifEmpty { null },
            totalConquistasSteam = total,
            conquistas = conquistas,
            situacaoConquistas = when {
                situacao == SituacaoConquistas.SEM_CHAVE && total == 0 -> SituacaoConquistas.NENHUMA
                else -> situacao
            },
            situacaoConta = situacaoConta
        )
    }

    /** Capa vertical (600x900) quando existe; senão a imagem de cabeçalho da loja é usada. */
    private suspend fun escolherCapa(appId: Int): String? {
        val url = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900.jpg"
        return runCatching { loja.verificar(url).isSuccessful }.getOrDefault(false)
            .let { existe -> if (existe) url else null }
    }

    private suspend fun carregarConquistas(appId: Int): Pair<List<ConquistaSteam>, SituacaoConquistas> {
        if (!temChave) return emptyList<ConquistaSteam>() to SituacaoConquistas.SEM_CHAVE
        return try {
            coroutineScope {
                val percentuaisAsync = async {
                    // raridade é um extra: se falhar, as conquistas vêm sem ela
                    runCatching { web.percentuais(appId) }.getOrNull()
                        ?.achievementpercentages?.achievements.orEmpty()
                        .associate { it.name to it.percent?.toFloatOrNull() }
                }
                val lista = web.conquistas(chave, appId).game?.availableGameStats?.achievements.orEmpty()
                val percentuais = percentuaisAsync.await()
                val conquistas = lista.map { c ->
                    ConquistaSteam(
                        nome = c.displayName?.trim()?.ifEmpty { null } ?: c.name,
                        descricao = c.description?.trim()?.ifEmpty { null }
                            ?: if (c.hidden == 1) "Conquista secreta" else "",
                        iconeUrl = urlIcone(c.icon),
                        raridade = percentuais[c.name],
                        apiName = c.name
                    )
                }
                conquistas to if (conquistas.isEmpty()) SituacaoConquistas.NENHUMA else SituacaoConquistas.IMPORTADAS
            }
        } catch (e: HttpException) {
            val situacao = if (e.code() == 401 || e.code() == 403) SituacaoConquistas.CHAVE_INVALIDA
            else SituacaoConquistas.FALHA
            emptyList<ConquistaSteam>() to situacao
        } catch (e: java.io.IOException) {
            emptyList<ConquistaSteam>() to SituacaoConquistas.FALHA
        }
    }

    companion object {
        private val cliente = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        private fun retrofit(baseUrl: String) = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        internal val loja: SteamStoreApi by lazy {
            retrofit("https://store.steampowered.com/").create(SteamStoreApi::class.java)
        }
        internal val web: SteamWebApi by lazy {
            retrofit("https://api.steampowered.com/").create(SteamWebApi::class.java)
        }

        private val ICONE_STEAM = Regex("""/images/apps/(\d+)/([^/?]+)(\?.*)?$""")

        /**
         * A Web API ainda devolve os ícones no domínio antigo (steamcdn-a.akamaihd.net), que
         * hoje responde 404. Converte qualquer ícone de conquista da Steam para o endereço
         * atual da CDN; endereços de fora da Steam (ou já no formato novo) não mudam.
         */
        fun urlIcone(url: String?): String? {
            if (url.isNullOrBlank()) return null
            if (!url.contains("steam") || url.contains("/community_assets/")) return url
            val m = ICONE_STEAM.find(url) ?: return url
            val (appId, arquivo) = m.destructured
            return "https://shared.akamai.steamstatic.com/community_assets/images/apps/$appId/$arquivo"
        }

        private val MESES = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

        /** Converte a data da loja em pt-BR ("4/set./2025") para ISO ("2025-09-04"). */
        fun dataIso(texto: String?): String? {
            val m = Regex("""(\d{1,2})\s*/\s*([a-zç]{3})\.?\s*/\s*(\d{4})""")
                .find(texto?.lowercase() ?: return null) ?: return null
            val (dia, mesTexto, ano) = m.destructured
            val mes = MESES.indexOf(mesTexto) + 1
            if (mes == 0 || dia.toInt() !in 1..31) return null
            return String.format(Locale.US, "%s-%02d-%02d", ano, mes, dia.toInt())
        }
    }
}
