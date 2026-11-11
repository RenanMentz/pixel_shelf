package com.pixelshelf.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titulo: String,
    val plataforma: String,
    val status: String,        // ver GameStatus.OPCOES
    val nota: Float,           // 0 a 10
    val horasJogadas: Float
)

object GameStatus {
    val OPCOES = listOf("Jogando", "Zerado", "Na fila")
}
