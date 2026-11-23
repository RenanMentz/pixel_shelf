package com.pixelshelf.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Popula a biblioteca com jogos de exemplo na primeira execução.
 * Os dados (capas, datas, conquistas em pt-BR e raridade) vêm da Steam e ficam em
 * assets/jogos_iniciais.json.
 */
object DadosIniciais {

    private const val PREFS = "pixel_shelf"
    private const val CHAVE = "dados_iniciais_carregados"

    suspend fun carregarSeNecessario(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(CHAVE, false)) return@withContext

        val db = AppDatabase.get(context)
        val json = context.assets.open("jogos_iniciais.json").bufferedReader().use { it.readText() }
        val jogos = JSONArray(json)

        db.withTransaction {
            for (i in 0 until jogos.length()) {
                val j = jogos.getJSONObject(i)
                val titulo = j.getString("titulo")
                val plataforma = j.getString("plataforma")
                // não duplica caso o usuário já tenha cadastrado o mesmo jogo
                if (db.gameDao().contarDuplicados(titulo, plataforma, 0) > 0) continue

                val id = db.gameDao().inserir(
                    Game(
                        titulo = titulo,
                        plataforma = plataforma,
                        status = j.getString("status"),
                        nota = j.optFloatOrNull("nota"),
                        horasJogadas = j.getDouble("horasJogadas").toFloat(),
                        capaUrl = j.optStringOrNull("capaUrl"),
                        dataLancamento = j.optStringOrNull("dataLancamento"),
                        desenvolvedora = j.optStringOrNull("desenvolvedora"),
                        genero = j.optStringOrNull("genero"),
                        descricao = j.optStringOrNull("descricao")
                    )
                ).toInt()

                val conquistas = j.getJSONArray("conquistas")
                db.conquistaDao().inserirTodas(List(conquistas.length()) { k ->
                    val c = conquistas.getJSONObject(k)
                    Conquista(
                        jogoId = id,
                        nome = c.getString("nome"),
                        descricao = c.getString("descricao"),
                        iconeUrl = c.optStringOrNull("icone"),
                        raridade = c.optFloatOrNull("raridade"),
                        obtida = c.getBoolean("obtida")
                    )
                })
            }
        }
        prefs.edit().putBoolean(CHAVE, true).apply()
    }

    private fun JSONObject.optStringOrNull(nome: String) =
        if (isNull(nome)) null else optString(nome).takeIf { it.isNotBlank() }

    private fun JSONObject.optFloatOrNull(nome: String) =
        if (isNull(nome)) null else optDouble(nome).toFloat()
}
