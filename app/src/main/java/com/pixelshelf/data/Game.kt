package com.pixelshelf.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cada jogo pertence a um usuário: contas diferentes têm bibliotecas separadas. */
@Entity(tableName = "games", indices = [Index("usuarioId")])
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val usuarioId: Int,
    val titulo: String,
    val plataforma: String,
    val status: String,             // ver GameStatus
    val nota: Float?,               // 0 a 10; null quando o jogo ainda está "Na fila"
    val horasJogadas: Float,
    val capaUrl: String? = null,    // URL da internet ou arquivo local (file://)
    val dataLancamento: String? = null, // formato ISO: yyyy-MM-dd
    val desenvolvedora: String? = null,
    val genero: String? = null,
    val descricao: String? = null,
    @ColumnInfo(defaultValue = "0")
    val favorito: Boolean = false,
    val steamAppId: Int? = null     // id do jogo na Steam (importado ou sincronizado)
)

object GameStatus {
    const val JOGANDO = "Jogando"
    const val ZERADO = "Zerado"
    const val NA_FILA = "Na fila"
    val OPCOES = listOf(JOGANDO, ZERADO, NA_FILA)
}

object Plataformas {
    val OPCOES = listOf(
        "PC", "PlayStation 5", "PlayStation 4", "Xbox Series X|S", "Xbox One",
        "Nintendo Switch", "Mobile"
    )
}

/** Jogo + progresso das conquistas, usado na listagem da biblioteca. */
data class GameResumo(
    @androidx.room.Embedded val game: Game,
    val totalConquistas: Int,
    val conquistasObtidas: Int
) {
    /** Platina = todas as conquistas do jogo desbloqueadas (calculado, não fica no banco). */
    val platinado: Boolean get() = totalConquistas > 0 && conquistasObtidas == totalConquistas
}
