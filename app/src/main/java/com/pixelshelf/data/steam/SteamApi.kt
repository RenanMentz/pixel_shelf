package com.pixelshelf.data.steam

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * API pública da loja Steam (não precisa de chave).
 * Base: https://store.steampowered.com/
 */
interface SteamStoreApi {

    /** Busca jogos pelo nome. */
    @GET("api/storesearch/")
    suspend fun buscar(
        @Query("term") termo: String,
        @Query("l") idioma: String = "brazilian",
        @Query("cc") pais: String = "BR"
    ): BuscaDto

    /** Detalhes de um jogo. A resposta é um objeto cuja chave é o próprio appid. */
    @GET("api/appdetails")
    suspend fun detalhes(
        @Query("appids") appId: Int,
        @Query("l") idioma: String = "brazilian",
        @Query("cc") pais: String = "BR"
    ): Map<String, DetalhesEnvelopeDto>

    /** Só verifica se uma imagem existe (usado para escolher a capa). */
    @HEAD
    suspend fun verificar(@Url url: String): Response<Void>
}

/**
 * Steam Web API. GetSchemaForGame precisa de chave; o percentual global não.
 * Base: https://api.steampowered.com/
 */
interface SteamWebApi {

    /** Lista completa de conquistas do jogo, com nome e descrição em português. */
    @GET("ISteamUserStats/GetSchemaForGame/v2/")
    suspend fun conquistas(
        @Query("key") chave: String,
        @Query("appid") appId: Int,
        @Query("l") idioma: String = "brazilian"
    ): EsquemaDto

    /** Percentual de jogadores que desbloquearam cada conquista (raridade). */
    @GET("ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/")
    suspend fun percentuais(@Query("gameid") appId: Int): PercentuaisDto
}

// ---------- DTOs (formato do JSON da Steam; campos anuláveis porque a Steam omite vários) ----------

data class BuscaDto(val items: List<ItemBuscaDto>?)

data class ItemBuscaDto(
    val id: Int,
    val type: String?,
    val name: String?,
    @SerializedName("tiny_image") val imagemPequena: String?
)

data class DetalhesEnvelopeDto(val success: Boolean, val data: DetalhesDto?)

data class DetalhesDto(
    val name: String?,
    @SerializedName("short_description") val descricaoCurta: String?,
    val developers: List<String>?,
    val genres: List<GeneroDto>?,
    @SerializedName("release_date") val lancamento: LancamentoDto?,
    @SerializedName("header_image") val imagemCabecalho: String?,
    val achievements: ResumoConquistasDto?
)

data class GeneroDto(val description: String?)

data class LancamentoDto(
    @SerializedName("coming_soon") val emBreve: Boolean?,
    val date: String?   // ex.: "24/fev./2017"
)

data class ResumoConquistasDto(val total: Int?)

data class EsquemaDto(val game: EsquemaJogoDto?)

data class EsquemaJogoDto(val availableGameStats: EstatisticasJogoDto?)

data class EstatisticasJogoDto(val achievements: List<ConquistaDto>?)

data class ConquistaDto(
    val name: String,           // id interno, liga com o percentual
    val displayName: String?,
    val description: String?,   // a Steam omite em conquistas secretas
    val icon: String?,
    val hidden: Int?
)

data class PercentuaisDto(val achievementpercentages: ListaPercentuaisDto?)

data class ListaPercentuaisDto(val achievements: List<PercentualDto>?)

data class PercentualDto(val name: String, val percent: String?)
